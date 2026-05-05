package com.fraudstream.processor.api;

import com.fraudstream.processor.entity.FlaggedTransactionEntity;
import com.fraudstream.processor.repository.FlaggedTransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flags")
public class FlagController {

    private final FlaggedTransactionRepository repo;

    public FlagController(FlaggedTransactionRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<FlaggedTransactionEntity> getFlags(
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) String accountId,
            @RequestParam(defaultValue = "50") int limit) {

        if (ruleId != null && !ruleId.isBlank()) {
            return repo.findByRuleIdOrderByFlaggedAtDesc(ruleId).stream().limit(limit).toList();
        }
        if (accountId != null && !accountId.isBlank()) {
            return repo.findByAccountIdOrderByFlaggedAtDesc(accountId).stream().limit(limit).toList();
        }
        return repo.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "flaggedAt"))).getContent();
    }
}
