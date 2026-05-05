# AWS Deployment Guide

This project is built locally on Docker Compose. The architecture maps cleanly onto AWS managed services. This document outlines what production deployment looks like, with Terraform snippets where relevant.

> **Note:** This deployment is documented but not executed in the demo. Running it incurs AWS costs (MSK alone is ~$200/month for the smallest cluster).

## Service mapping

| Local | AWS |
|-------|-----|
| Kafka (Bitnami) | Amazon MSK or MSK Serverless |
| Stream Processor (local JVM) | ECS Fargate behind ALB |
| Producer (local JVM) | ECS Fargate (scheduled task or always-on) |
| PostgreSQL (Docker) | RDS PostgreSQL Multi-AZ |
| Prometheus | Amazon Managed Prometheus (AMP) |
| Grafana | Amazon Managed Grafana (AMG) |
| Dashboard (Vite dev) | S3 + CloudFront |

## Network layout

- VPC with public + private subnets across 2 AZs
- ALB and CloudFront in public subnets
- ECS tasks, RDS, MSK in private subnets
- VPC endpoints for ECR, S3, Secrets Manager, CloudWatch (avoids NAT egress costs)

## Terraform skeleton

```hcl
module "msk" {
  source             = "terraform-aws-modules/msk-kafka-cluster/aws"
  name               = "fraud-stream"
  kafka_version      = "3.7.x"
  number_of_broker_nodes = 2
  broker_node_instance_type = "kafka.t3.small"
  broker_node_client_subnets = module.vpc.private_subnets
}

resource "aws_db_instance" "postgres" {
  identifier        = "fraud-stream"
  engine            = "postgres"
  engine_version    = "16.3"
  instance_class    = "db.t4g.medium"
  allocated_storage = 100
  multi_az          = true
  db_name           = "fraudstream"
  username          = "fraud"
  manage_master_user_password = true   # rotated by Secrets Manager
  vpc_security_group_ids = [aws_security_group.rds.id]
  db_subnet_group_name   = aws_db_subnet_group.this.name
}

resource "aws_ecs_service" "processor" {
  name            = "stream-processor"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.processor.arn
  desired_count   = 2
  launch_type     = "FARGATE"
  network_configuration {
    subnets         = module.vpc.private_subnets
    security_groups = [aws_security_group.processor.id]
  }
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
}
```

## Configuration injection

- Bootstrap servers come from MSK output → SSM Parameter Store
- DB credentials from Secrets Manager (rotated automatically)
- All wired into the task definition as `secrets` (not `environment`) so values stay out of CloudWatch logs

## Scaling

- Stream processor: ECS Service Auto Scaling on `kafka_consumer_lag` custom metric
- Partitions: start with 6 per topic, gives 6 parallel processor tasks max
- RDS: vertical scaling first, then add a read replica for the dashboard query path

## CI/CD

GitHub Actions workflow in `.github/workflows/deploy.yml`:

1. Maven build + tests
2. Build container image, push to ECR with both `:latest` and `:<sha>` tags
3. Update ECS task definition with new image tag
4. `aws ecs update-service` with `--force-new-deployment`
5. Wait for steady-state, fail the build if circuit breaker rolls back

## Cost estimate

| Component | Monthly |
|-----------|---------|
| MSK Serverless (low usage) | $50 |
| ECS Fargate (2 tasks, 1 vCPU, 2GB) | $40 |
| RDS db.t4g.medium Multi-AZ | $130 |
| ALB + CloudFront | $25 |
| AMP + AMG | $20 |
| **Total (idle/demo load)** | **~$265** |

Numbers are rough — actual cost depends on traffic, retention, and data transfer.
