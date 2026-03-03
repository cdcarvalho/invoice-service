package com.backing.invoice.repository;

import com.backing.invoice.domain.Lancamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;

public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {
    @Query("SELECT COALESCE(SUM(l.valor), 0) FROM Lancamento l WHERE l.contaId = :contaId")
    BigDecimal sumValorByContaId(@Param("contaId") Long contaId);
}
