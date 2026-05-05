# Free-Tier Cloud Deployment

> Total estimated cost: $0/month

This guide shows how to run the complete FraudStream pipeline in the cloud at zero cost by composing free tiers from several providers.

---

## Services

| Component | Free-Tier Provider | Free Tier Limits |
|---|---|---|
| Kafka | Confluent Cloud or Upstash Kafka | Confluent: 5 GB storage, 1 MB/s throughput; Upstash: 10,000 messages/day |
| Postgres | Neon | 0.5 GB storage, 1 compute unit, branching |
| App hosting | Railway | 500 hours/month, 512 MB RAM, 1 vCPU |
| Dashboard | Vercel | Unlimited hobby projects, 100 GB bandwidth |
| Monitoring | Grafana Cloud | 10,000 metrics series, 14-day retention |

---

## Recommended Stack

**Confluent Cloud + Neon + Railway + Vercel** is the recommended combination because:

- Confluent Cloud's free tier supports the Kafka Streams client natively (standard Kafka protocol, no SDK lock-in).
- Neon provides a fully managed PostgreSQL 16 instance with connection pooling via PgBouncer, which suits the low-concurrency free tier.
- Railway supports Docker-based deployments and injects environment variables from a GUI, making it trivial to wire up secrets.
- Vercel provides zero-config Next.js / Vite deployments and a global CDN for the React dashboard.

---

## Step-by-Step Setup

### 1. Confluent Cloud (Kafka)

1. Sign up at [confluent.io/get-started](https://www.confluent.io/get-started/).
2. Create a **Basic** cluster in the region closest to your other services.
3. Create two topics: `transactions` (6 partitions) and `fraud-alerts` (1 partition).
4. Under **API Keys**, generate a key pair for the stream processor.
5. Note the **Bootstrap Server** URL (e.g. `pkc-xxxxx.us-east-1.aws.confluent.cloud:9092`).

Environment variables to configure:

```env
KAFKA_BOOTSTRAP_SERVERS=pkc-xxxxx.us-east-1.aws.confluent.cloud:9092
KAFKA_SECURITY_PROTOCOL=SASL_SSL
KAFKA_SASL_MECHANISM=PLAIN
KAFKA_SASL_JAAS_USERNAME=<api-key>
KAFKA_SASL_JAAS_PASSWORD=<api-secret>
```

### 2. Neon (PostgreSQL)

1. Sign up at [neon.tech](https://neon.tech).
2. Create a project and note the **Connection String** from the dashboard.
3. The Flyway migrations in `stream-processor/src/main/resources/db/migration/` will run automatically on startup.

Environment variables to configure:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>.neon.tech/neondb?sslmode=require
SPRING_DATASOURCE_USERNAME=<user>
SPRING_DATASOURCE_PASSWORD=<password>
```

### 3. Railway (Stream Processor)

1. Sign up at [railway.app](https://railway.app).
2. Click **New Project** -> **Deploy from GitHub Repo** and select your fork.
3. Set the **Root Directory** to the repo root and the **Dockerfile Path** to `Dockerfile` (see below).
4. Add the environment variables from steps 1 and 2 in the **Variables** tab.
5. Railway will build and deploy on every push to `main`.

Environment variables to configure (in addition to Kafka + Postgres vars above):

```env
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=cloud
```

### 4. Vercel (React Dashboard)

1. Sign up at [vercel.com](https://vercel.com).
2. Click **Add New Project** and import your GitHub repo.
3. Set the **Root Directory** to `dashboard`.
4. Vercel auto-detects Vite and sets the build command to `npm run build` and output directory to `dist`.
5. Add the WebSocket URL as an environment variable:

```env
VITE_WS_URL=wss://<your-railway-app>.railway.app/ws
```

### 5. Grafana Cloud (Monitoring)

1. Sign up at [grafana.com/auth/sign-up](https://grafana.com/auth/sign-up).
2. Create a free stack (includes Prometheus remote write endpoint + Grafana UI).
3. In your stream processor configuration, add:

```env
MANAGEMENT_PROMETHEUS_METRICS_EXPORT_PUSHGATEWAY_ENABLED=true
MANAGEMENT_PROMETHEUS_METRICS_EXPORT_PUSHGATEWAY_BASE_URL=https://prometheus-prod-xx-prod-us-central-0.grafana.net/api/prom
MANAGEMENT_PROMETHEUS_METRICS_EXPORT_PUSHGATEWAY_USERNAME=<grafana-instance-id>
MANAGEMENT_PROMETHEUS_METRICS_EXPORT_PUSHGATEWAY_PASSWORD=<grafana-api-key>
```

4. Import `docker/grafana/dashboards/fraud-stream.json` via **Dashboards -> Import** in your Grafana Cloud instance.

---

## Dockerfile for Stream Processor

```dockerfile
# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY stream-processor/pom.xml .
COPY stream-processor/src ./src
RUN apk add --no-cache maven && mvn -B package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/stream-processor-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

The multi-stage build keeps the final image small (~200 MB) by discarding the JDK and Maven after compilation.

---

## Cost Comparison

| Environment | Monthly Cost | Notes |
|---|---|---|
| Local (Docker Compose) | Free | Requires laptop to stay on; not accessible externally |
| Free cloud (this guide) | ~$0 | Free tier limits apply; suitable for demos and development |
| AWS (equivalent setup) | ~$100/month | MSK ($60) + RDS t3.micro ($15) + EC2 t3.small ($15) + CloudWatch ($10) |
| GCP (equivalent setup) | ~$90/month | Pub/Sub + Cloud SQL + Cloud Run + Cloud Monitoring |
| Azure (equivalent setup) | ~$95/month | Event Hubs + Azure Database for PostgreSQL + Container Apps |

Free tier limits to watch:

- **Confluent Cloud**: The 5 GB storage limit is hit after ~50 million small transactions. Archive old data or increase retention limits.
- **Railway**: 500 execution hours/month resets on the 1st. The stream processor runs 24/7, so you will exhaust ~336 hours of that budget in a 14-day period — consider pausing the service when not in use.
- **Neon**: The 0.5 GB storage limit accommodates roughly 2 million alert rows. Run `DELETE FROM fraud_alerts WHERE created_at < now() - interval '7 days';` periodically.

---

## Confluent Cloud vs Upstash Kafka

| Feature | Confluent Cloud Free | Upstash Kafka Free |
|---|---|---|
| Protocol | Native Kafka (all clients) | Native Kafka + REST API |
| Message limit | None (bandwidth-limited) | 10,000 messages/day |
| Storage | 5 GB | 256 MB |
| Partitions | Up to 6 | Up to 10 |
| Retention | Configurable (7 days default) | 1 day |
| Kafka Streams support | Full | Full |
| Schema Registry | Free tier available | Not included |
| Multi-region | No (single cluster) | No (single cluster) |
| Best for | High-throughput demos, production prototypes | Very low-volume demos, REST-first architectures |

**Recommendation**: Use Confluent Cloud if you plan to replay the full CSV dataset or run the pipeline for more than a few hours at a time. Use Upstash Kafka if you only need to send occasional test transactions and prefer a pay-per-use pricing model when you eventually scale beyond the free tier.
