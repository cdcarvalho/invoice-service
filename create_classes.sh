#!/bin/bash
BASE="src/main/java/com/backing/invoice"

cat << 'JEOF' > $BASE/domain/Conta.java
package com.backing.invoice.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CONTAS")
public class Conta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String status;
    @Column(name = "data_fechamento", nullable = false) private Integer dataFechamento;
    @Column(name = "created_at", insertable = false, updatable = false) private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getDataFechamento() { return dataFechamento; }
    public void setDataFechamento(Integer dataFechamento) { this.dataFechamento = dataFechamento; }
}
JEOF

cat << 'JEOF' > $BASE/domain/Fatura.java
package com.backing.invoice.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "FATURAS")
public class Fatura {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "conta_id", nullable = false) private Long contaId;
    @Column(name = "mes_referencia", nullable = false) private String mesReferencia;
    @Column(name = "valor_total", nullable = false) private BigDecimal valorTotal;
    @Column(nullable = false) private String status;
    @Column(name = "created_at", insertable = false, updatable = false) private LocalDateTime createdAt;
    public Fatura() {}
    public Fatura(Long contaId, String mesReferencia, BigDecimal valorTotal, String status) {
        this.contaId = contaId; this.mesReferencia = mesReferencia; this.valorTotal = valorTotal; this.status = status;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getContaId() { return contaId; }
    public void setContaId(Long contaId) { this.contaId = contaId; }
    public String getMesReferencia() { return mesReferencia; }
    public void setMesReferencia(String mesReferencia) { this.mesReferencia = mesReferencia; }
    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
JEOF

cat << 'JEOF' > $BASE/repository/ContaRepository.java
package com.backing.invoice.repository;

import com.backing.invoice.domain.Conta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContaRepository extends JpaRepository<Conta, Long> {
    @Modifying
    @Query("UPDATE Conta c SET c.status = 'PROCESSADA' WHERE c.id = :id AND c.status = 'ABERTA'")
    int updateStatusToProcessada(@Param("id") Long id);
}
JEOF

cat << 'JEOF' > $BASE/repository/FaturaRepository.java
package com.backing.invoice.repository;

import com.backing.invoice.domain.Fatura;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaturaRepository extends JpaRepository<Fatura, Long> {
}
JEOF

cat << 'JEOF' > $BASE/batch/BatchConfig.java
package com.backing.invoice.batch;

import com.backing.invoice.domain.Conta;
import com.backing.invoice.domain.Fatura;
import com.backing.invoice.repository.ContaRepository;
import com.backing.invoice.repository.FaturaRepository;
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
    public ItemProcessor<Conta, Fatura> processor() {
        return conta -> {
            String mesRef = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            return new Fatura(conta.getId(), mesRef, new BigDecimal("150.00"), "CALCULADA");
        };
    }

    @Bean
    public ItemWriter<Fatura> writer(ContaRepository contaRepository, FaturaRepository faturaRepository) {
        return faturas -> {
            for (Fatura fatura : faturas) {
                try {
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
JEOF

bash ./mvnw clean install -DskipTests

