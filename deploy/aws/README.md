# Deploying to AWS

Target architecture:

```
Client ──HTTPS──▶ ALB ──▶ ECS Fargate service (policy-rag container, private subnets)
                                  │
                                  ├──▶ Amazon RDS for PostgreSQL 16 (pgvector extension)
                                  ├──▶ AWS Secrets Manager (DB password, JWT secret, OpenAI key)
                                  └──▶ CloudWatch Logs
```

## 1. Database
1. Create an RDS PostgreSQL 16 instance (pgvector is supported on RDS for PostgreSQL 15.2+).
2. Create the database `policydocs`. Flyway creates the `vector` extension and the catalogue
   table on first start; Spring AI creates the `vector_store` table and HNSW index.

## 2. Secrets
Create these in Secrets Manager and reference them from `ecs-task-definition.json`:
`policy-rag/db-password`, `policy-rag/jwt-secret` (≥ 32 random chars), `policy-rag/openai-api-key`.
Grant the task execution role `secretsmanager:GetSecretValue` on them.

## 3. Container registry and service
1. Create an ECR repository, e.g. `policy-rag`.
2. Create an ECS cluster and a Fargate service behind an ALB target group on port 8080.
   Use `/actuator/health/readiness` as the target-group health check path.
3. Replace the `<ACCOUNT_ID>`, `<REGION>` and `<RDS_ENDPOINT>` placeholders in
   `ecs-task-definition.json`.

## 4. CI/CD
`.github/workflows/deploy-aws.yml` builds the image, pushes it to ECR and rolls the ECS service.
Configure GitHub OIDC with an IAM role allowed to push to ECR and update the service, then set
repository variables `AWS_REGION`, `AWS_ROLE_ARN`, `ECR_REPOSITORY`, `ECS_CLUSTER`, `ECS_SERVICE`
and run the workflow manually.

## Production notes
- Replace the demo users in `application.yml` with a real identity provider (Cognito, Okta, ...);
  the API already validates standard JWTs, so only the decoder configuration needs to change
  (`spring.security.oauth2.resourceserver.jwt.issuer-uri`) and the `roles` claim mapping.
- Set `SEED_ENABLED=false` so sample documents are not loaded.
