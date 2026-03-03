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
