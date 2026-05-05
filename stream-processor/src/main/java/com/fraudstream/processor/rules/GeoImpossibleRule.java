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
public class GeoImpossibleRule implements FraudRule {

    @Value("${fraud.rules.geo-impossible.max-km:500}") private double maxKm;
    @Value("${fraud.rules.geo-impossible.window-minutes:30}") private int windowMinutes;

    private record TxLocation(double lat, double lon, Instant ts, String country) {}

    private final ConcurrentHashMap<String, Deque<TxLocation>> windows = new ConcurrentHashMap<>();

    @Override
    public String ruleId() { return "GEO_IMPOSSIBLE"; }

    @Override
    public Optional<FraudFlag> evaluate(Transaction tx) {
        Instant now = tx.timestamp() != null ? tx.timestamp() : Instant.now();
        Instant cutoff = now.minusSeconds(windowMinutes * 60L);
        TxLocation current = new TxLocation(tx.lat(), tx.lon(), now, tx.country());

        Deque<TxLocation> deque = windows.computeIfAbsent(tx.cardId(), k -> new ArrayDeque<>());
        synchronized (deque) {
            for (TxLocation prior : deque) {
                if (prior.ts().isBefore(cutoff)) continue;
                double km = haversineKm(prior.lat(), prior.lon(), current.lat(), current.lon());
                if (km > maxKm) {
                    return Optional.of(new FraudFlag(
                        tx.txId(), tx.accountId(), ruleId(), 0.95,
                        "Card used %.0fkm from previous location (%s -> %s) within %d minutes"
                            .formatted(km, prior.country(), tx.country(), windowMinutes),
                        Instant.now()
                    ));
                }
            }
            deque.addLast(current);
            while (!deque.isEmpty() && deque.peekFirst().ts().isBefore(cutoff)) {
                deque.pollFirst();
            }
        }
        return Optional.empty();
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon/2) * Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }
}
