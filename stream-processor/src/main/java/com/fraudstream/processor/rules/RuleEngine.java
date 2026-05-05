package com.fraudstream.processor.rules;

import com.fraudstream.processor.model.FraudFlag;
import com.fraudstream.processor.model.Transaction;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final List<FraudRule> rules;
    private final MeterRegistry meters;

    public RuleEngine(List<FraudRule> rules, MeterRegistry meters) {
        this.rules = rules;
        this.meters = meters;
        log.info("RuleEngine initialized with {} rules: {}",
                rules.size(),
                rules.stream().map(FraudRule::ruleId).toList());
    }

    /**
     * Evaluate a transaction against all registered rules.
     * Returns all flags that fired (a single tx can trigger multiple rules).
     */
    public List<FraudFlag> evaluate(Transaction tx) {
        List<FraudFlag> flags = new ArrayList<>();
        for (FraudRule rule : rules) {
            Timer.Sample sample = Timer.start(meters);
            try {
                Optional<FraudFlag> flag = rule.evaluate(tx);
                flag.ifPresent(f -> {
                    flags.add(f);
                    meters.counter("flags_fired_total", "rule", rule.ruleId()).increment();
                });
            } catch (Exception e) {
                log.error("Rule {} threw on tx {}", rule.ruleId(), tx.txId(), e);
                meters.counter("rule_errors_total", "rule", rule.ruleId()).increment();
            } finally {
                sample.stop(meters.timer("rule_eval_seconds", "rule", rule.ruleId()));
            }
        }
        meters.counter("tx_processed_total").increment();
        return flags;
    }
}
