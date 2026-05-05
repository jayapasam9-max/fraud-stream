package com.fraudstream.processor.repository;

import com.fraudstream.processor.entity.FlaggedTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface FlaggedTransactionRepository extends JpaRepository<FlaggedTransactionEntity, Long> {

    List<FlaggedTransactionEntity> findTop50ByOrderByFlaggedAtDesc();

    List<FlaggedTransactionEntity> findByRuleIdOrderByFlaggedAtDesc(String ruleId);

    List<FlaggedTransactionEntity> findByAccountIdOrderByFlaggedAtDesc(String accountId);

    long countByFlaggedAtAfter(Instant since);

    @Query("SELECT f.ruleId, COUNT(f) FROM FlaggedTransactionEntity f WHERE f.flaggedAt > :since GROUP BY f.ruleId")
    List<Object[]> countByRuleIdAfter(Instant since);
}
