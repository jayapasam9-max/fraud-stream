-- Flagged transactions, partitioned by month for efficient pruning
CREATE TABLE flagged_transactions (
    id              BIGSERIAL,
    tx_id           VARCHAR(64)   NOT NULL,
    account_id      VARCHAR(64)   NOT NULL,
    rule_id         VARCHAR(64)   NOT NULL,
    score           NUMERIC(5,3)  NOT NULL,
    reason          TEXT          NOT NULL,
    flagged_at      TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (id, flagged_at)
) PARTITION BY RANGE (flagged_at);

-- Initial partitions; in production a job rolls these forward monthly
CREATE TABLE flagged_transactions_2026_05 PARTITION OF flagged_transactions
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE flagged_transactions_2026_06 PARTITION OF flagged_transactions
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

CREATE INDEX idx_flagged_account     ON flagged_transactions (account_id, flagged_at DESC);
CREATE INDEX idx_flagged_rule        ON flagged_transactions (rule_id, flagged_at DESC);
CREATE INDEX idx_flagged_at          ON flagged_transactions (flagged_at DESC);

-- Audit log for every transaction processed (sampled in production)
CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    tx_id           VARCHAR(64)   NOT NULL,
    correlation_id  VARCHAR(64)   NOT NULL,
    rules_evaluated INT           NOT NULL,
    flags_fired     INT           NOT NULL,
    eval_ms         INT           NOT NULL,
    processed_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_processed_at  ON audit_log (processed_at DESC);
CREATE INDEX idx_audit_tx            ON audit_log (tx_id);
