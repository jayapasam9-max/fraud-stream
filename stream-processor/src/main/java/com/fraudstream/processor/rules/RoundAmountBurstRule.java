package com.fraudstream.processor.rules;

import com.fraudstream.processor.model.FraudFlag;
import com.fraudstream.processor.model.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoundAmountBurstRule implements FraudRule {

    @Value("${fraud.rules.round-amount-burst.window-minutes:10}") private int windowMinutes;
    @Value("${fraud.rules.round-amount-burst.threshold:3}") private int threshold;

    private final ConcurrentHashMap<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    @Override
    public String ruleId() { return "ROUND_AMOUNT_BURST"; }

    private boolean isRound(BigDecimal amount) {
        return amount != null
            && amount.compareTo(BigDecimal.TEN) >= 0
            && amount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    public Optional<FraudFlag> evaluate(Transaction tx) {
        if (!isRound(tx.amount())) return Optional.empty();

        Instant now = tx.timestamp() != null ? tx.timestamp() : Instant.now();
        Instant cutoff = now.minusSeconds(windowMinutes * 60L);

        Deque<Instant> deque = windows.computeIfAbsent(tx.accountId(), k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(now);
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            int count = deque.size();
            if (count >= threshold) {
                return Optional.of(new FraudFlag(
                    tx.txId(), tx.accountId(), ruleId(), 0.7,
                    "%d round-number transactions in %d-minute window (amount: $%s)"
                        .formatted(count, windowMinutes, tx.amount().toPlainString()),
                    Instant.now()
                ));
            }
        }
        return Optional.empty();
    }
}
