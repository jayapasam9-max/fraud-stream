package com.fraudstream.processor.api;

import com.fraudstream.processor.repository.FlaggedTransactionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final FlaggedTransactionRepository repo;

    public StatsController(FlaggedTransactionRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public StatsResponse getStats() {
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long totalFlags = repo.countByFlaggedAtAfter(oneHourAgo);
        List<Object[]> byRule = repo.countByRuleIdAfter(oneHourAgo);
        Map<String, Long> flagsByRule = new HashMap<>();
        for (Object[] row : byRule) {
            flagsByRule.put((String) row[0], (Long) row[1]);
        }
        return new StatsResponse(totalFlags, flagsByRule);
    }

    public record StatsResponse(long totalFlags, Map<String, Long> flagsByRule) {}
}
