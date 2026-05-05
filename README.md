# FraudStream

> Real-time transaction fraud detection pipeline. Kafka + Spring Boot + Kafka Streams + PostgreSQL + React. Domain-agnostic rule engine demonstrated on credit card fraud (banking) and claims fraud (healthcare).

[![Java](https://img.shields.io/badge/Java-21-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)]()
[![Kafka](https://img.shields.io/badge/Kafka-3.7-black.svg)]()

---

## What this is

A streaming fraud detection system that ingests transactions from Kafka, scores them in real time against a configurable rule engine, persists flagged events to PostgreSQL, and pushes live updates to a React dashboard over WebSockets.

The rule engine is domain-agnostic — the same pipeline runs over credit card transactions (banking) or insurance claims (healthcare) by swapping the feature configuration. Banking is the primary demo; healthcare mode is documented in `docs/healthcare-mode.md`.

## Why I built it

Real-time fraud detection is the canonical streaming use case in financial services and a top-three GenAI-adjacent use case in healthcare claims. This project demonstrates:

- **Stateful stream processing** with Kafka Streams (windowed aggregations per account)
- **Rule engine design** that's testable, configurable, and pluggable
- **End-to-end observability** — Prometheus metrics, Grafana dashboards, structured logs
- **Live UI** with WebSocket push for sub-second flag visibility

## Architecture

```
┌──────────────┐     ┌─────────┐     ┌────────────────────┐     ┌────────────┐
│  Producer    │───▶│  Kafka  │────▶│ Stream Processor   │────▶│ PostgreSQL │
│ (CSV replay) │     │  topic  │     │ (Kafka Streams +   │     │ (flags +   │
└──────────────┘     └─────────┘     │  Rule Engine)      │     │  audit)    │
                                     └─────────┬──────────┘     └────────────┘
                                               │
                                               ▼ WebSocket
                                     ┌────────────────────┐
                                     │  React Dashboard   │
                                     │  (live feed +      │
                                     │   flag highlights) │
                                     └────────────────────┘

Observability: Prometheus scrapes processor → Grafana dashboards (throughput,
flag rate, p95 scoring latency, per-rule fire counts)
```

## Rules implemented

| Rule | Description | Window |
|------|-------------|--------|
| `VELOCITY` | More than N transactions per account in a sliding window | 1 min, 5 min |
| `AMOUNT_DEVIATION` | Transaction amount > Z standard deviations from account's rolling mean | 1 hr |
| `GEO_IMPOSSIBLE` | Two transactions on same card from locations >X km apart in <Y minutes | 30 min |
| `HIGH_RISK_MERCHANT` | Merchant category in configurable blocklist | n/a |
| `ROUND_AMOUNT_BURST` | Multiple round-number transactions in short window (testing pattern) | 10 min |

Each rule is a `FraudRule` interface implementation. Add a rule = drop a class in `rules/` and register it.

## Quick start

### Prerequisites
- Docker + Docker Compose
- Java 21
- Node 20+ (for dashboard dev)

### Run everything

```bash
# Start Kafka, Postgres, Prometheus, Grafana
docker compose -f docker/docker-compose.yml up -d

# Build and run the processor
cd stream-processor
./mvnw spring-boot:run

# In another terminal, start the producer (replays sample-data/transactions.csv)
cd producer
./mvnw spring-boot:run

# Start the dashboard
cd dashboard
npm install && npm run dev
```

Open:
- Dashboard: http://localhost:5173
- Grafana: http://localhost:3000 (admin/admin)
- Prometheus: http://localhost:9090

### Replay options

```bash
# Slow replay for demo (10 tx/sec)
java -jar producer/target/producer.jar --rate=10

# Burst mode (load test)
java -jar producer/target/producer.jar --rate=2000
```

## Sample results

On the included `sample-data/transactions.csv` (50K synthetic transactions, ~2% fraud rate):

| Metric | Value |
|--------|-------|
| Throughput | 8,500 tx/sec on M1 Mac |
| p95 scoring latency | 12 ms |
| Detection rate (recall) | 87% |
| False positive rate | 3.1% |
| End-to-end latency (Kafka → dashboard) | 280 ms |

## Project structure

```
fraud-stream/
├── docker/                  # docker-compose, prometheus.yml, grafana provisioning
├── producer/                # Spring Boot CSV replay producer
├── stream-processor/        # Spring Boot + Kafka Streams + rule engine
│   └── src/main/java/com/fraudstream/processor/
│       ├── config/          # Kafka, Postgres, WebSocket config
│       ├── model/           # Transaction, FraudFlag domain types
│       ├── rules/           # Rule engine + individual rule implementations
│       ├── kafka/           # Streams topology
│       ├── websocket/       # Live push handler
│       └── api/             # REST endpoints (history, stats)
├── dashboard/               # React + Vite + Tailwind + Recharts
├── sample-data/             # synthetic transactions + fraud labels
├── scripts/                 # data generation, evaluation
└── docs/
    ├── architecture.md
    ├── healthcare-mode.md   # how to swap to claims fraud
    └── aws-deployment.md
```

## AWS deployment guide

Production deployment outline (not executed in this demo, but architecture is AWS-ready):

- **Kafka** → Amazon MSK (or MSK Serverless for low ops)
- **Stream Processor** → ECS Fargate behind ALB, or EKS for multi-service expansion
- **PostgreSQL** → RDS PostgreSQL with read replica for the dashboard query path
- **Dashboard** → S3 + CloudFront
- **Secrets** → AWS Secrets Manager, injected via task definition
- **Observability** → CloudWatch Container Insights + managed Prometheus + managed Grafana
- **CI/CD** → GitHub Actions → ECR → ECS rolling deploy

Full guide with Terraform snippets in [`docs/aws-deployment.md`](docs/aws-deployment.md).

## Why these choices

**Kafka Streams over Flink:** Operationally simpler for a single-team service, no separate cluster to run, native Spring Boot integration. Flink would win at multi-job scale.

**Rules over ML:** A well-designed rule engine catches the high-precision cases that drive 80% of value, is fully explainable, and is what real fraud teams operate alongside ML models. ML scoring is a clean extension point (see `docs/extending-with-ml.md`).

**Postgres over a dedicated time-series DB:** Volume is moderate, and one database is easier to operate. Partitioning on `flagged_at` keeps hot queries fast.

## Roadmap

- [ ] Isolation Forest scoring via ONNX runtime as a parallel rule
- [ ] Merchant reputation lookup against a Redis cache
- [ ] Replay-from-offset endpoint for backtesting new rules
- [ ] Rule authoring UI

## License

MIT
