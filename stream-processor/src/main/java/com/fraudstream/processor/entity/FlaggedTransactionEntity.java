package com.fraudstream.processor.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "flagged_transactions")
public class FlaggedTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tx_id", nullable = false, length = 64)
    private String txId;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Column(name = "rule_id", nullable = false, length = 64)
    private String ruleId;

    @Column(name = "score", nullable = false, precision = 5, scale = 3)
    private BigDecimal score;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "flagged_at", nullable = false)
    private Instant flaggedAt;

    public FlaggedTransactionEntity() {}

    public FlaggedTransactionEntity(String txId, String accountId, String ruleId,
                                    BigDecimal score, String reason, Instant flaggedAt) {
        this.txId = txId;
        this.accountId = accountId;
        this.ruleId = ruleId;
        this.score = score;
        this.reason = reason;
        this.flaggedAt = flaggedAt;
    }

    public Long getId() { return id; }
    public String getTxId() { return txId; }
    public String getAccountId() { return accountId; }
    public String getRuleId() { return ruleId; }
    public BigDecimal getScore() { return score; }
    public String getReason() { return reason; }
    public Instant getFlaggedAt() { return flaggedAt; }
}
