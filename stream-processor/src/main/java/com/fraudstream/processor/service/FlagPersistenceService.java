package com.fraudstream.processor.service;

import com.fraudstream.processor.entity.AuditLogEntity;
import com.fraudstream.processor.entity.FlaggedTransactionEntity;
import com.fraudstream.processor.model.FraudFlag;
import com.fraudstream.processor.repository.AuditLogRepository;
import com.fraudstream.processor.repository.FlaggedTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class FlagPersistenceService {

    private final FlaggedTransactionRepository flagRepo;
    private final AuditLogRepository auditRepo;

    public FlagPersistenceService(FlaggedTransactionRepository flagRepo,
                                   AuditLogRepository auditRepo) {
        this.flagRepo = flagRepo;
        this.auditRepo = auditRepo;
    }

    @Transactional
    public void persist(FraudFlag flag, String correlationId, int rulesEvaluated, long evalMs) {
        FlaggedTransactionEntity entity = new FlaggedTransactionEntity(
            flag.txId(),
            flag.accountId(),
            flag.ruleId(),
            BigDecimal.valueOf(flag.score()),
            flag.reason(),
            flag.flaggedAt() != null ? flag.flaggedAt() : Instant.now()
        );
        flagRepo.save(entity);

        AuditLogEntity audit = new AuditLogEntity(
            flag.txId(),
            correlationId,
            rulesEvaluated,
            1,
            (int) Math.min(evalMs, Integer.MAX_VALUE),
            Instant.now()
        );
        auditRepo.save(audit);
    }
}
