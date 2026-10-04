# AWS infrastructure (Terraform)

```
                Internet
                   │
          ┌────────▼────────┐
          │  Application LB  │  public subnets, :80 / :443 (ACM)
          └────────┬────────┘
                   │ only ALB SG → :8080
          ┌────────▼────────┐        ┌──────────────────────┐
          │  ECS Fargate     │──────▶│ OpenAI / OpenRouter   │ (HTTPS egress)
          │  policy-rag task │        └──────────────────────┘
          └───┬─────────┬───┘
   only app SG│:5432    │ secrets injected at start
     ┌────────▼───┐ ┌───▼───────────────┐   ┌──────────────┐
     │ RDS Postgres│ │ Secrets Manager   │   │ CloudWatch   │
     │ 16+pgvector │ │ DB pw, JWT, keys  │   │ Logs/Insights│
     │ private sub.│ └───────────────────┘   └──────────────┘
     └─────────────┘
   ECR (images, scan on push) ◀── GitHub Actions (OIDC role, no stored AWS keys)
```

| File | Creates |
|---|---|
| `network.tf` | VPC, 2 public + 2 private subnets in 2 AZs, internet gateway, security groups (ALB → app → DB chain) |
| `database.tf` | RDS PostgreSQL 16 (encrypted, private, SSL forced, 7-day backups, password managed by RDS in Secrets Manager) |
| `secrets.tf` | JWT signing key, admin/demo passwords (random) and an empty slot for the LLM API key |
| `ecr.tf` | Container registry with vulnerability scanning and a keep-last-10 lifecycle rule |
| `alb.tf` | Load balancer, target group with readiness health check, HTTP listener (+ HTTPS when a certificate is given) |
| `ecs.tf` | Cluster, log group, task execution/task IAM roles, task definition (env + secrets), service with circuit-breaker rollback |
| `github_oidc.tf` | GitHub OIDC provider and a least-privilege deploy role scoped to this repo's `main` branch |

## What you need

- An AWS account and the AWS CLI logged in (`aws sts get-caller-identity` works)
- Terraform ≥ 1.6
- An OpenAI or OpenRouter API key
- (Optional) a domain + ACM certificate for HTTPS

## Cost

This is **not free**. A rough monthly estimate for the defaults in a typical region: ALB ~$18–22,
Fargate (0.5 vCPU / 1 GB, 1 task) ~$15–20, RDS db.t4g.micro + 20 GB ~$15–20, plus small amounts for
Secrets Manager, CloudWatch and ECR. Check the AWS Pricing Calculator for your region, and note that
new accounts may have free-tier credits. **Set an AWS Budget alert before applying**, and run
`terraform destroy` when you no longer need the demo.

There is no NAT gateway (it would add ~$35/month): tasks run in public subnets with a public IP for
outbound calls, but their security group only accepts traffic from the load balancer.

## Deploy

```bash
cd infra/aws
cp terraform.tfvars.example terraform.tfvars   # edit region, models, repo
terraform init
terraform plan
terraform apply
```

Store the LLM API key (once):
```bash
aws secretsmanager put-secret-value \
  --secret-id "$(terraform output -raw llm_api_key_secret_name)" \
  --secret-string 'sk-...'
```

Configure GitHub → **Settings → Secrets and variables → Actions → Variables**:

| Variable | Value |
|---|---|
| `AWS_REGION` | your region, e.g. `ap-south-1` |
| `AWS_ROLE_ARN` | `terraform output -raw github_actions_role_arn` |
| `ECR_REPOSITORY` | `terraform output -raw ecr_repository_name` |
| `ECS_CLUSTER` | `terraform output -raw ecs_cluster_name` |
| `ECS_SERVICE` | `terraform output -raw ecs_service_name` |
| `ECS_TASK_FAMILY` | `terraform output -raw ecs_task_family` |

Then run **Actions → Deploy to AWS (ECS Fargate) → Run workflow**. Until the first image is pushed
the service can't start tasks, which is expected.

Check it:
```bash
curl "$(terraform output -raw app_url)/actuator/health"
aws secretsmanager get-secret-value --secret-id "$(terraform output -raw demo_password_secret_name)" \
  --query SecretString --output text      # password for alice / hana / felix
```

Logs: CloudWatch → Log groups → `/ecs/policy-rag-dev`.

## Production hardening (next steps)

- Private subnets + NAT gateway (or VPC endpoints) for tasks
- HTTPS only (`certificate_arn`) and AWS WAF with rate limiting on `/api/ask`
- `db_multi_az = true`, `db_deletion_protection = true`, S3 remote state with locking
- Replace demo users with Amazon Cognito (the API already validates standard JWTs)
- ECS service auto scaling on CPU / request count
