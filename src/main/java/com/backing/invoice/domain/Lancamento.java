package com.backing.invoice.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "LANCAMENTOS")
public class Lancamento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "conta_id", nullable = false) private Long contaId;
    @Column(nullable = false) private BigDecimal valor;
    @Column(name = "data_lancamento", nullable = false) private LocalDateTime dataLancamento;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getContaId() { return contaId; }
    public void setContaId(Long contaId) { this.contaId = contaId; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public LocalDateTime getDataLancamento() { return dataLancamento; }
    public void setDataLancamento(LocalDateTime dataLancamento) { this.dataLancamento = dataLancamento; }
}
