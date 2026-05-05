package com.fraudstream.processor.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Transaction(
        String txId,
        String cardId,
        String accountId,
        BigDecimal amount,
        String merchant,
        String mcc,
        String country,
        double lat,
        double lon,
        Instant timestamp,
        Boolean isFraudLabel  // present only in replayed datasets, ignored at runtime
) {}
