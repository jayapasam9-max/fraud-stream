# 7-Day Build Plan

Track progress by checking off acceptance criteria. Each day ends with a working artifact you can demo.

---

## Day 1 — Skeleton + Kafka + Producer

**Goal:** A producer publishes synthetic credit card transactions to a Kafka topic. A consumer logs them.

**Tasks:**
- [ ] `docker-compose.yml` with Kafka (KRaft mode, no Zookeeper), Postgres, Prometheus, Grafana
- [ ] Generate `sample-data/transactions.csv` — 50K rows, ~2% labeled fraud, columns: `tx_id, card_id, account_id, amount, merchant, mcc, country, lat, lon, timestamp, is_fraud`
- [ ] Producer service (Spring Boot) reads CSV, publishes to `transactions` topic at configurable rate
- [ ] Stream processor service (Spring Boot, Kafka Streams) consumes and logs

**Acceptance:** `docker compose up` works. Run producer at 100 tx/sec, see them in processor logs.

---

## Day 2 — Postgres + Domain Model + Velocity Rule

**Goal:** First rule firing, flagged transactions saved to Postgres.

**Tasks:**
- [ ] Postgres schema: `flagged_transactions`, `audit_log`, `rule_metrics` tables, partition `flagged_transactions` by month
- [ ] JPA entities, Flyway migrations
- [ ] `FraudRule` interface + `RuleEngine` that fans transactions through all registered rules
- [ ] First rule: `VelocityRule` — flag if account has >5 tx in 60 sec (Kafka Streams windowed count)
- [ ] Persist flags to Postgres

**Acceptance:** Run producer with synthetic burst pattern, see flags written to `flagged_transactions`.

---

## Day 3 — Rule Engine Expansion

**Goal:** Four working rules, all unit-tested.

**Tasks:**
- [ ] `AmountDeviationRule` — Z-score against rolling 1hr mean per account (use Kafka Streams state store)
- [ ] `GeoImpossibleRule` — two tx >500km apart in <30 min on same card
- [ ] `HighRiskMerchantRule` — MCC code in configurable blocklist (load from `application.yml`)
- [ ] `RoundAmountBurstRule` — 3+ round-number tx ($100, $500, etc.) in 10 min
- [ ] Unit tests for each rule with `TopologyTestDriver`
- [ ] Each rule emits a `FraudFlag` with `ruleId`, `score`, `reason` string

**Acceptance:** Curl `/api/flags?ruleId=GEO_IMPOSSIBLE` returns flagged events. All rule unit tests pass.

---

## Day 4 — REST API + WebSocket Push

**Goal:** Backend exposes flags via REST and pushes new ones live.

**Tasks:**
- [ ] `GET /api/flags?since=&ruleId=&accountId=&limit=` — paginated flag history
- [ ] `GET /api/stats` — last 1hr summary: total tx, total flags, per-rule counts, p95 latency
- [ ] WebSocket endpoint `/ws/flags` — push every new flag to subscribed clients
- [ ] CORS config for localhost:5173

**Acceptance:** `wscat` to `/ws/flags`, run producer, see flags streaming in JSON. REST endpoints return data.

---

## Day 5 — React Dashboard

**Goal:** Live-updating dashboard. This is what people see in screenshots — make it look good.

**Tasks:**
- [ ] Vite + React + Tailwind + Recharts scaffold
- [ ] Live transaction feed (last 50, oldest scrolls off) with flag highlights and reason chips
- [ ] Stats header: tx/sec gauge, total flags today, flag rate %
- [ ] Per-rule bar chart (last 1 hr)
- [ ] Time-series chart: flag count per minute (last 30 min)
- [ ] WebSocket reconnection logic
- [ ] Dark mode by default (looks better in screenshots)

**Acceptance:** `npm run dev`, run producer, watch flags light up live. Take screenshots for README.

---

## Day 6 — Observability

**Goal:** Prometheus + Grafana dashboards. This separates the project from a toy demo.

**Tasks:**
- [ ] Micrometer + Prometheus endpoint on processor
- [ ] Custom metrics: `tx_processed_total`, `flags_fired_total{rule}`, `rule_eval_seconds{rule}`, `kafka_lag`
- [ ] Provision Grafana dashboard JSON in `docker/grafana/dashboards/`
- [ ] Dashboard panels: throughput, p95 latency per rule, flag rate, per-rule fire counts, Kafka consumer lag
- [ ] Structured JSON logs with `correlationId` per transaction

**Acceptance:** Open Grafana at localhost:3000, dashboard auto-loads, data populates as producer runs.

---

## Day 7 — Healthcare Mode + Polish + Ship

**Goal:** Healthcare framing documented, repo is presentable, demo recorded.

**Tasks:**
- [ ] `docs/healthcare-mode.md` — how to swap rules to claims fraud (provider velocity, upcoding pattern, duplicate billing). Include sample claims CSV in `sample-data/claims.csv`
- [ ] `docs/aws-deployment.md` — MSK/ECS/RDS deployment with Terraform snippets
- [ ] `docs/architecture.md` — diagrams, data flow, rule engine extension guide
- [ ] README screenshots, GIF of live dashboard, sample metrics table populated with real numbers
- [ ] GitHub Actions: build + test on push
- [ ] 2-minute Loom recording linked in README
- [ ] `git push`, add topics: `kafka`, `spring-boot`, `fraud-detection`, `streaming`, `kafka-streams`

**Acceptance:** Repo looks shippable. Send the link to a friend, ask if it's clear from README alone what the project does.

---

## Cuts if you fall behind

| If behind on... | Cut |
|-----------------|-----|
| Day 3 | Drop `RoundAmountBurstRule`, keep 3 rules |
| Day 5 | Use `react-vite` template directly, skip the time-series chart |
| Day 6 | Skip Grafana provisioning, just expose `/actuator/prometheus` and screenshot a manual dashboard |
| Day 7 | Skip healthcare-mode CSV, just keep the doc explaining how to extend |

## What NOT to add

- ML scoring (already on roadmap, don't expand scope)
- Auth (this is a local demo, not production)
- Multi-tenant config (kills 2 days, adds nothing for the demo)
- A web-based rule editor (it's on the roadmap for a reason)
