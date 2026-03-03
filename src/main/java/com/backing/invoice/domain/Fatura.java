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
