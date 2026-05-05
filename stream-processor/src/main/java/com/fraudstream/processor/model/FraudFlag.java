package com.fraudstream.processor.model;

import java.time.Instant;

public record FraudFlag(
        String txId,
        String accountId,
        String ruleId,
        double score,
        String reason,
        Instant flaggedAt
) {}
