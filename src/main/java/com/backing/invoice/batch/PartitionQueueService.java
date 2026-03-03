package com.backing.invoice.batch;

import com.backing.invoice.domain.BatchPartitionQueue;
import com.backing.invoice.repository.BatchPartitionQueueRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PartitionQueueService {

    @PersistenceContext
    private EntityManager entityManager;

    private final BatchPartitionQueueRepository repository;
    private final String workerId = UUID.randomUUID().toString();

    public PartitionQueueService(BatchPartitionQueueRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<BatchPartitionQueue> claimPartition() {
        String sql = """
            WITH first_row AS (
                SELECT id
                FROM BATCH_PARTITION_QUEUE
                WHERE status = 'PENDING'
                ORDER BY id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
            )
            UPDATE BATCH_PARTITION_QUEUE
            SET status = 'CLAIMED', worker_pod_id = :workerId, updated_at = :now
            WHERE id = (SELECT id FROM first_row)
            RETURNING id
        """;

        try {
            // A query agora retorna apenas o ID da partição capturada
            var nativeQuery = entityManager.createNativeQuery(sql, Long.class);
            nativeQuery.setParameter("workerId", workerId);
            nativeQuery.setParameter("now", LocalDateTime.now());
            
            Long claimedId = (Long) nativeQuery.getSingleResult();
            
            // Com o ID, buscamos a entidade completa para retornar
            return repository.findById(claimedId);
        } catch (NoResultException e) {
            return Optional.empty(); // Nenhuma partição disponível para capturar
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updatePartitionStatus(Long partitionId, String status) {
        repository.findById(partitionId).ifPresent(partition -> {
            partition.setStatus(status);
            partition.setUpdatedAt(LocalDateTime.now());
            repository.save(partition);
        });
    }

    @Transactional
    public void createPartitions(long jobExecutionId, int gridSize) {
        for (int i = 0; i < gridSize; i++) {
            BatchPartitionQueue partition = new BatchPartitionQueue();
            partition.setJobExecutionId(jobExecutionId);
            partition.setPartitionId(i);
            partition.setStatus("PENDING");
            repository.save(partition);
        }
    }
}
