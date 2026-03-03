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
