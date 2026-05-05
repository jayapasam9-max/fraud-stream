# FraudStream Architecture

## System Overview

FraudStream is a real-time fraud detection pipeline built on Apache Kafka Streams, Spring Boot 3.3, and Java 21. Transactions are ingested via a CSV producer, evaluated by a rule engine in the stream processor, persisted to PostgreSQL, and pushed live to a React dashboard over WebSocket.

```
+----------+       +-------+       +------------------+       +----------+
| Producer | ----> | Kafka | ----> | Stream Processor | ----> | Postgres |
+----------+       +-------+       +------------------+       +----------+
  (CSV replay)     (topic:           (Kafka Streams +              |
                  transactions)      Rule Engine)           +----------+
                                          |                 | WebSocket|
                                          +---------------> | Server   |
                                                            +----------+
                                                                 |
                                                          +---------------+
                                                          | React Dashboard|
                                                          | (Recharts +    |
                                                          |  Tailwind CSS) |
                                                          +---------------+
```

## Data Flow

1. The **Producer** reads transactions from a CSV file and publishes each row as a JSON message to the Kafka topic `transactions`.
2. **Kafka** durably stores the messages and fans them out to consumer groups.
3. The **Stream Processor** consumes from `transactions` using Kafka Streams. For each message it:
   a. Deserializes the JSON payload into a `Transaction` domain object.
   b. Runs the transaction through the **Rule Engine**, evaluating all registered `FraudRule` implementations.
   c. If any rule fires, a `FraudAlert` is produced to the `fraud-alerts` topic and persisted to PostgreSQL.
   d. Emits Prometheus metrics (`tx_processed_total`, `flags_fired_total`, `rule_eval_seconds`).
4. The **WebSocket Server** (embedded in the stream processor) pushes `FraudAlert` events to all connected browser clients in real time.
5. The **React Dashboard** subscribes to the WebSocket, renders a live feed of alerts, and displays Recharts time-series graphs for throughput and flag rate.

## Rule Engine Design

### FraudRule Interface

```java
public interface FraudRule {
    /**
     * Unique rule identifier used in metrics and alert payloads.
     */
    String ruleId();

    /**
     * Evaluate the transaction against this rule.
     *
     * @param tx      the transaction under evaluation
     * @param context stateful context (e.g. Kafka Streams state stores)
     * @return Optional.of(alert) if the rule fires, Optional.empty() otherwise
     */
    Optional<FraudAlert> evaluate(Transaction tx, RuleContext context);
}
```

### Built-in Rules

| Rule ID | Logic |
|---|---|
| `VELOCITY` | More than 5 transactions from the same card within 60 seconds |
| `AMOUNT_DEVIATION` | Transaction amount exceeds 3 standard deviations from the card's 30-day mean |
| `GEO_IMPOSSIBLE` | Two consecutive transactions from the same card are geographically impossible given the elapsed time |
| `HIGH_RISK_MERCHANT` | Merchant MCC code is on the configurable high-risk list |
| `ROUND_AMOUNT_BURST` | 3+ round-dollar transactions (e.g. $100.00, $200.00) within 10 minutes from the same card |

### Adding a New Rule

1. Create a class that implements `FraudRule`.
2. Annotate it with `@Component` (Spring will auto-detect it).
3. Inject any Kafka Streams state stores you need via `RuleContext`.
4. Return `Optional.of(new FraudAlert(...))` when the rule triggers.

```java
@Component
public class LargeAmountRule implements FraudRule {

    @Override
    public String ruleId() {
        return "LARGE_AMOUNT";
    }

    @Override
    public Optional<FraudAlert> evaluate(Transaction tx, RuleContext ctx) {
        if (tx.amount().compareTo(new BigDecimal("10000")) > 0) {
            return Optional.of(FraudAlert.of(tx, ruleId(), "Amount exceeds $10,000"));
        }
        return Optional.empty();
    }
}
```

No changes are needed anywhere else — the `RuleEngine` discovers all `FraudRule` beans at startup and runs them for every transaction.

## Observability

### Prometheus Metrics

| Metric | Type | Labels | Description |
|---|---|---|---|
| `tx_processed_total` | Counter | `application` | Total transactions consumed from Kafka |
| `flags_fired_total` | Counter | `application`, `rule` | Total fraud flags raised, broken down by rule |
| `rule_eval_seconds` | Histogram | `application`, `rule` | Latency of each rule evaluation |
| `alerts_persisted_total` | Counter | `application` | Total alerts written to PostgreSQL |
| `ws_clients_connected` | Gauge | `application` | Current WebSocket client connections |

Metrics are scraped by Prometheus at `http://stream-processor:8080/actuator/prometheus`.

### Grafana Dashboard Panels

The provisioned dashboard (`docker/grafana/dashboards/fraud-stream.json`) contains four panels:

1. **Transaction Throughput (tx/sec)** — time-series of `rate(tx_processed_total[1m])`.
2. **Flags Fired by Rule** — bar gauge showing cumulative `flags_fired_total` per rule.
3. **Rule Eval Latency p95** — stat panel showing the 95th-percentile histogram quantile for rule evaluation time.
4. **Flag Rate %** — stat panel showing the ratio of flags fired to transactions processed over a 5-minute window.

The dashboard auto-refreshes every 5 seconds and shows the last 30 minutes by default.

## Extension Points

### Adding ML Scoring as a FraudRule

To integrate a machine-learning model (e.g. an XGBoost model served via a REST endpoint or embedded via ONNX Runtime):

```java
@Component
public class MlScoringRule implements FraudRule {

    private final MlScoringClient client; // HTTP or gRPC client to your model server

    public MlScoringRule(MlScoringClient client) {
        this.client = client;
    }

    @Override
    public String ruleId() {
        return "ML_SCORE";
    }

    @Override
    public Optional<FraudAlert> evaluate(Transaction tx, RuleContext ctx) {
        double score = client.score(tx.toFeatureVector());
        if (score > 0.85) {
            return Optional.of(
                FraudAlert.of(tx, ruleId(),
                    "ML model confidence: " + String.format("%.2f%%", score * 100))
            );
        }
        return Optional.empty();
    }
}
```

Because `MlScoringRule` is just another `FraudRule` bean, it participates in the same metrics, alerting, and WebSocket push pipeline automatically. To avoid adding latency to the hot path, consider making the ML call asynchronous and caching scores in a Kafka Streams state store keyed by card ID.
