package com.backing.invoice.batch;

import com.backing.invoice.domain.Conta;
import com.backing.invoice.domain.Fatura;
import com.backing.invoice.repository.ContaRepository;
import com.backing.invoice.repository.FaturaRepository;
import com.backing.invoice.repository.LancamentoRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
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
    public Job invoiceJob(JobRepository jobRepository, Step masterStep) {
        return new JobBuilder("invoiceJob", jobRepository)
                .start(masterStep)
                .build();
    }

    @Bean
    public Step masterStep(JobRepository jobRepository, Step workerStep, Partitioner partitioner) {
        return new StepBuilder("masterStep", jobRepository)
                .partitioner(workerStep.getName(), partitioner)
                .step(workerStep)
                .gridSize(10)
                .taskExecutor(taskExecutor())
                .build();
    }

    @Bean
    public Partitioner partitioner() {
        return gridSize -> {
            Map<String, ExecutionContext> map = new HashMap<>();
            for (int i = 0; i < gridSize; i++) {
                ExecutionContext context = new ExecutionContext();
                context.putInt("partitionId", i);
                context.putInt("totalPartitions", gridSize);
                map.put("partition" + i, context);
            }
            return map;
        };
    }

    @Bean
    public TaskExecutor taskExecutor() {
        return new SimpleAsyncTaskExecutor("spring_batch");
    }

    @Bean
    public Step workerStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                           JdbcCursorItemReader<Conta> reader, ItemProcessor<Conta, Fatura> processor,
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
    public JdbcCursorItemReader<Conta> reader(DataSource dataSource,
                                              @Value("#{stepExecutionContext['partitionId']}") Integer partitionId,
                                              @Value("#{stepExecutionContext['totalPartitions']}") Integer totalPartitions) {
        log.info("Iniciando leitura para particao {}", partitionId);
        int today = LocalDate.now().getDayOfMonth();
        return new JdbcCursorItemReaderBuilder<Conta>()
                .name("contaReader")
                .dataSource(dataSource)
                .sql("SELECT id, status, data_fechamento FROM CONTAS WHERE status = 'ABERTA' AND data_fechamento = " + today + " AND MOD(id, " + totalPartitions + ") = " + partitionId + " ORDER BY id")
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
