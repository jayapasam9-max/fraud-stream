package com.fraudstream.processor.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tx_id", nullable = false, length = 64)
    private String txId;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "rules_evaluated", nullable = false)
    private int rulesEvaluated;

    @Column(name = "flags_fired", nullable = false)
    private int flagsFired;

    @Column(name = "eval_ms", nullable = false)
    private int evalMs;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public AuditLogEntity() {}

    public AuditLogEntity(String txId, String correlationId, int rulesEvaluated,
                          int flagsFired, int evalMs, Instant processedAt) {
        this.txId = txId;
        this.correlationId = correlationId;
        this.rulesEvaluated = rulesEvaluated;
        this.flagsFired = flagsFired;
        this.evalMs = evalMs;
        this.processedAt = processedAt;
    }

    public Long getId() { return id; }
    public String getTxId() { return txId; }
    public String getCorrelationId() { return correlationId; }
    public int getRulesEvaluated() { return rulesEvaluated; }
    public int getFlagsFired() { return flagsFired; }
    public int getEvalMs() { return evalMs; }
    public Instant getProcessedAt() { return processedAt; }
}
