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
public class VelocityRule implements FraudRule {

    @Value("${fraud.rules.velocity.window-seconds:60}") private int windowSeconds;
    @Value("${fraud.rules.velocity.threshold:5}") private int threshold;

    private final ConcurrentHashMap<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    @Override
    public String ruleId() { return "VELOCITY"; }

    @Override
    public Optional<FraudFlag> evaluate(Transaction tx) {
        Instant now = tx.timestamp() != null ? tx.timestamp() : Instant.now();
        Instant cutoff = now.minusSeconds(windowSeconds);

        Deque<Instant> deque = windows.computeIfAbsent(tx.accountId(), k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(now);
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            int count = deque.size();
            if (count > threshold) {
                return Optional.of(new FraudFlag(
                    tx.txId(), tx.accountId(), ruleId(), 0.9,
                    "%d transactions in %ds window (threshold: %d)".formatted(count, windowSeconds, threshold),
                    Instant.now()
                ));
            }
        }
        return Optional.empty();
    }
}
