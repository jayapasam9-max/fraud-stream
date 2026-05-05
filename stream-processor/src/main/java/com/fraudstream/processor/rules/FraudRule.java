package com.fraudstream.processor.rules;

import com.fraudstream.processor.model.FraudFlag;
import com.fraudstream.processor.model.Transaction;

import java.util.Optional;

/**
 * A single fraud detection rule. Implementations should be stateless at the bean
 * level — any state needed for windowed evaluation lives in Kafka Streams state
 * stores (see VelocityRule for the pattern).
 *
 * To add a new rule:
 *   1. Implement this interface
 *   2. Annotate with @Component
 *   3. Register state stores in KafkaStreamsConfig if windowed
 *   4. Add unit tests with TopologyTestDriver
 */
public interface FraudRule {

    /** Stable identifier. Surfaces in metrics, logs, API responses. */
    String ruleId();

    /**
     * Evaluate the transaction. Return a FraudFlag if the rule fires,
     * empty otherwise. Should complete in single-digit milliseconds.
     */
    Optional<FraudFlag> evaluate(Transaction tx);
}
