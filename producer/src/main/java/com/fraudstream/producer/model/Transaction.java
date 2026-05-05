package com.fraudstream.producer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
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
        Boolean isFraudLabel
) {}
