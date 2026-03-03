package com.backing.invoice.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "BATCH_PARTITION_QUEUE")
public class BatchPartitionQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_execution_id", nullable = false)
    private Long jobExecutionId;

    @Column(name = "partition_id", nullable = false)
    private int partitionId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "worker_pod_id")
    private String workerPodId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getJobExecutionId() {
        return jobExecutionId;
    }

    public void setJobExecutionId(Long jobExecutionId) {
        this.jobExecutionId = jobExecutionId;
    }

    public int getPartitionId() {
        return partitionId;
    }

    public void setPartitionId(int partitionId) {
        this.partitionId = partitionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getWorkerPodId() {
        return workerPodId;
    }

    public void setWorkerPodId(String workerPodId) {
        this.workerPodId = workerPodId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
