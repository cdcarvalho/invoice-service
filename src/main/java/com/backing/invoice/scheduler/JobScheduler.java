package com.backing.invoice.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class JobScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    private final JobLauncher jobLauncher;
    private final Job masterJob;
    private final Job workerJob;

    public JobScheduler(JobLauncher jobLauncher, 
                        @Qualifier("masterJob") Job masterJob, 
                        @Qualifier("workerJob") Job workerJob) {
        this.jobLauncher = jobLauncher;
        this.masterJob = masterJob;
        this.workerJob = workerJob;
    }

    @Scheduled(fixedRate = 120000)
    public void runMasterJob() {
        log.info("SCHEDULER: Tentando iniciar o masterJob.");
        try {
            var jobParameters = new JobParametersBuilder()
                    .addString("runDate", LocalDate.now().format(DateTimeFormatter.ISO_DATE))
                    .toJobParameters();
            jobLauncher.run(masterJob, jobParameters);
        } catch (Exception e) {
            log.error("SCHEDULER: Erro ao tentar iniciar o masterJob. " +
                      "Isso pode ser normal se outro pod já iniciou a execução.", e);
        }
    }

    @Scheduled(fixedRate = 180000) // 300000 ms = 5 minutos
    public void runWorkerJob() {
        log.info("SCHEDULER: Iniciando workerJob para processar partições.");
        try {
            // Usar um ID único para cada execução permite que os workers rodem continuamente
            var jobParameters = new JobParametersBuilder()
                    .addString("runId", "worker-" + System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(workerJob, jobParameters);
        } catch (Exception e) {
            log.error("SCHEDULER: Erro ao executar o workerJob.", e);
        }
    }
}
