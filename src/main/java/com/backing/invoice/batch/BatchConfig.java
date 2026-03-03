package com.backing.invoice.batch;

import com.backing.invoice.domain.Conta;
import com.backing.invoice.domain.BatchPartitionQueue;
import com.backing.invoice.domain.Fatura;
import com.backing.invoice.repository.ContaRepository;
import com.backing.invoice.repository.FaturaRepository;
import com.backing.invoice.repository.LancamentoRepository;
import java.util.Optional;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.batch.item.ExecutionContext;

@Configuration
public class BatchConfig {

    private static final Logger log = LoggerFactory.getLogger(BatchConfig.class);

    @Bean
    public Job masterJob(JobRepository jobRepository, Step createPartitionsStep) {
        return new JobBuilder("masterJob", jobRepository)
                .start(createPartitionsStep)
                .build();
    }

    @Bean
    public Step createPartitionsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                     PartitionQueueService partitionQueueService) {
        return new StepBuilder("createPartitionsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    long jobExecutionId = chunkContext.getStepContext().getStepExecution().getJobExecutionId();
                    log.info("MASTER: Criando partições para Job Execution ID: {}", jobExecutionId);
                    int gridSize = 10; // Definindo o número de partições
                    partitionQueueService.createPartitions(jobExecutionId, gridSize);
                    log.info("MASTER: {} partições criadas com sucesso.", gridSize);
                    return org.springframework.batch.repeat.RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Job workerJob(JobRepository jobRepository, JobExecutionDecider workDecider, Step findWorkStep, Step workerStep, Step successStep, Step failureStep) {
        return new JobBuilder("workerJob", jobRepository)
                .start(findWorkStep)
                .next(workDecider)
                    .on("WORK_FOUND").to(workerStep)
                        .on("COMPLETED").to(successStep).next(findWorkStep) // Loop back after success
                        .on("*").to(failureStep).next(findWorkStep)      // Loop back after failure
                .from(workDecider)
                    .on("NO_WORK").end()
                .end()
                .build();
    }

    @Bean
    public JobExecutionDecider workDecider() {
        return (jobExecution, stepExecution) -> {
            boolean workFound = jobExecution.getExecutionContext().containsKey("partition.id");
            if (workFound) {
                return new FlowExecutionStatus("WORK_FOUND");
            } else {
                return new FlowExecutionStatus("NO_WORK");
            }
        };
    }

    @Bean
    public Step findWorkStep(JobRepository jobRepository, PartitionQueueService partitionQueueService, PlatformTransactionManager transactionManager) {
        return new StepBuilder("findWorkStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    Optional<BatchPartitionQueue> claimedPartitionOpt = partitionQueueService.claimPartition();
                    if (claimedPartitionOpt.isPresent()) {
                        BatchPartitionQueue partition = claimedPartitionOpt.get();
                        log.info("WORKER: Partição {} (JobExecId={}) capturada.", partition.getPartitionId(), partition.getJobExecutionId());
                        ExecutionContext jobContext = chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext();
                        jobContext.put("partition.id", partition.getId());
                        jobContext.put("partition.partitionId", partition.getPartitionId());
                    } else {
                        log.info("WORKER: Nenhuma partição disponível para processamento.");
                    }
                    return org.springframework.batch.repeat.RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step successStep(JobRepository jobRepository, PartitionQueueService partitionQueueService, PlatformTransactionManager transactionManager) {
        return new StepBuilder("successStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    ExecutionContext jobContext = chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext();
                    Long partitionId = jobContext.getLong("partition.id");
                    partitionQueueService.updatePartitionStatus(partitionId, "COMPLETED");
                    return org.springframework.batch.repeat.RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step failureStep(JobRepository jobRepository, PartitionQueueService partitionQueueService, PlatformTransactionManager transactionManager) {
        return new StepBuilder("failureStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    ExecutionContext jobContext = chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext();
                    Long partitionId = jobContext.getLong("partition.id");
                    partitionQueueService.updatePartitionStatus(partitionId, "FAILED");
                    return org.springframework.batch.repeat.RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step workerStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                           JdbcPagingItemReader<Conta> reader, ItemProcessor<Conta, Fatura> processor,
                           ItemWriter<Fatura> writer) {
        return new StepBuilder("workerStep", jobRepository)
                .<Conta, Fatura>chunk(100, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<Conta> reader(DataSource dataSource,
                                              @Value("#{jobExecutionContext['partition.partitionId']}") Integer partitionId,
                                              @Value("#{jobExecutionContext['totalPartitions'] ?: 10}") Integer totalPartitions) throws Exception {
        log.info("Iniciando leitura para particao {}", partitionId);
        int today = LocalDate.now().getDayOfMonth();

        SqlPagingQueryProviderFactoryBean factory = new SqlPagingQueryProviderFactoryBean();
        factory.setDataSource(dataSource);
        factory.setSelectClause("SELECT id, status, data_fechamento");
        factory.setFromClause("FROM CONTAS");
        factory.setWhereClause("WHERE status = 'ABERTA' AND data_fechamento = " + today + " AND MOD(id, " + totalPartitions + ") = " + partitionId);
        factory.setSortKey("id");

        PagingQueryProvider queryProvider = factory.getObject();

        return new JdbcPagingItemReaderBuilder<Conta>()
                .name("contaReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider)
                .pageSize(100) // Mesmo tamanho do chunk
                .rowMapper((rs, rowNum) -> {
                    Conta c = new Conta();
                    c.setId(rs.getLong("id"));
                    c.setStatus(rs.getString("status"));
                    c.setDataFechamento(rs.getInt("data_fechamento"));
                    return c;
                })
                .build();
    }

    @Bean
    public ItemProcessor<Conta, Fatura> processor(LancamentoRepository lancamentoRepository) {
        return conta -> {
            log.info("Processando conta_id: {} para calculo de fatura", conta.getId());
            String mesRef = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            BigDecimal valorTotal = lancamentoRepository.sumValorByContaId(conta.getId());
            return new Fatura(conta.getId(), mesRef, valorTotal, "CALCULADA");
        };
    }

    @Bean
    public ItemWriter<Fatura> writer(ContaRepository contaRepository, FaturaRepository faturaRepository) {
        return faturas -> {
            for (Fatura fatura : faturas) {
                try {
                    log.info("Salvando fatura e atualizando status para conta_id: {}", fatura.getContaId());
                    int updated = contaRepository.updateStatusToProcessada(fatura.getContaId());
                    if (updated > 0) {
                        faturaRepository.save(fatura);
                    }
                } catch (Exception e) {
                    log.error("Erro ao processar fatura para conta {}", fatura.getContaId(), e);
                }
            }
        };
    }
}
