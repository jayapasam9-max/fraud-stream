package com.fraudstream.processor.rules;

import com.fraudstream.processor.model.FraudFlag;
import com.fraudstream.processor.model.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Stateless rule. Flags transactions whose MCC (Merchant Category Code)
 * appears in a configurable blocklist. Configure in application.yml:
 *
 *   fraud:
 *     rules:
 *       high-risk-mcc: [6051, 7995, 5933]   # crypto, gambling, pawn shops
 */
@Component
public class HighRiskMerchantRule implements FraudRule {

    private final Set<String> blockedMccs;

    public HighRiskMerchantRule(@Value("${fraud.rules.high-risk-mcc:6051,7995,5933}") String mccs) {
        this.blockedMccs = Set.of(mccs.split(","));
    }

    @Override
    public String ruleId() {
        return "HIGH_RISK_MERCHANT";
    }

    @Override
    public Optional<FraudFlag> evaluate(Transaction tx) {
        if (tx.mcc() == null || !blockedMccs.contains(tx.mcc().trim())) {
            return Optional.empty();
        }
        return Optional.of(new FraudFlag(
                tx.txId(),
                tx.accountId(),
                ruleId(),
                0.6,
                "MCC %s is on the high-risk blocklist".formatted(tx.mcc()),
                Instant.now()
        ));
    }
}
