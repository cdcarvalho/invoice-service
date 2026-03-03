package com.backing.invoice.repository;

import com.backing.invoice.domain.Fatura;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaturaRepository extends JpaRepository<Fatura, Long> {
}
