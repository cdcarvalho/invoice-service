package com.backing.invoice.repository;

import com.backing.invoice.domain.BatchPartitionQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BatchPartitionQueueRepository extends JpaRepository<BatchPartitionQueue, Long> {
    
    List<BatchPartitionQueue> findByJobExecutionIdAndStatus(Long jobExecutionId, String status);

}
