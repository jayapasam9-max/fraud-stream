package com.fraudstream.processor.rules;

import com.fraudstream.processor.model.FraudFlag;
import com.fraudstream.processor.model.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AmountDeviationRule implements FraudRule {

    @Value("${fraud.rules.amount-deviation.z-score-threshold:3.0}") private double zThreshold;
    @Value("${fraud.rules.amount-deviation.window-minutes:60}") private int windowMinutes;

    private record Entry(double amount, Instant ts) {}

    private final ConcurrentHashMap<String, Deque<Entry>> windows = new ConcurrentHashMap<>();

    @Override
    public String ruleId() { return "AMOUNT_DEVIATION"; }

    @Override
    public Optional<FraudFlag> evaluate(Transaction tx) {
        if (tx.amount() == null) return Optional.empty();
        double amount = tx.amount().doubleValue();
        Instant now = tx.timestamp() != null ? tx.timestamp() : Instant.now();
        Instant cutoff = now.minusSeconds(windowMinutes * 60L);

        Deque<Entry> deque = windows.computeIfAbsent(tx.accountId(), k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(new Entry(amount, now));
            while (!deque.isEmpty() && deque.peekFirst().ts().isBefore(cutoff)) {
                deque.pollFirst();
            }
            if (deque.size() < 5) return Optional.empty();

            double mean = deque.stream().mapToDouble(Entry::amount).average().orElse(0);
            double variance = deque.stream().mapToDouble(e -> Math.pow(e.amount() - mean, 2)).average().orElse(0);
            double stdDev = Math.sqrt(variance);

            if (stdDev < 1.0) return Optional.empty();
            double zScore = (amount - mean) / stdDev;
            if (zScore > zThreshold) {
                double score = Math.min(1.0, zScore / 10.0);
                return Optional.of(new FraudFlag(
                    tx.txId(), tx.accountId(), ruleId(), score,
                    "Amount $%.2f is %.1f standard deviations above account mean $%.2f".formatted(amount, zScore, mean),
                    Instant.now()
                ));
            }
        }
        return Optional.empty();
    }
}
