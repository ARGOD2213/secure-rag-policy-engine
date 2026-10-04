variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "ap-south-1"
}

variable "project_name" {
  description = "Short name used as a prefix for all resources"
  type        = string
  default     = "policy-rag"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.20.0.0/16"
}

# ---- application -------------------------------------------------------------------------

variable "image_tag" {
  description = "Container image tag to run. CI/CD rolls new tags; Terraform only sets the initial one."
  type        = string
  default     = "latest"
}

variable "desired_count" {
  description = "Number of running tasks"
  type        = number
  default     = 1
}

variable "task_cpu" {
  description = "Fargate task CPU units (256, 512, 1024, ...)"
  type        = number
  default     = 512
}

variable "task_memory" {
  description = "Fargate task memory in MiB"
  type        = number
  default     = 1024
}

variable "ai_provider" {
  description = "openai (also covers OpenAI-compatible APIs such as OpenRouter)"
  type        = string
  default     = "openai"
}

variable "openai_base_url" {
  description = "https://api.openai.com for OpenAI, https://openrouter.ai/api for OpenRouter"
  type        = string
  default     = "https://api.openai.com"
}

variable "chat_model" {
  description = "Chat model id (OpenRouter: openai/gpt-4o-mini)"
  type        = string
  default     = "gpt-4o-mini"
}

variable "embedding_model" {
  description = "Embedding model id (OpenRouter: openai/text-embedding-3-small)"
  type        = string
  default     = "text-embedding-3-small"
}

variable "embedding_dimensions" {
  description = "Must match the embedding model output size"
  type        = number
  default     = 1536
}

variable "seed_enabled" {
  description = "Load the bundled sample policies on first start (handy for a demo)"
  type        = bool
  default     = true
}

# ---- database ----------------------------------------------------------------------------

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "RDS storage in GiB"
  type        = number
  default     = 20
}

variable "db_engine_version" {
  description = "PostgreSQL major version (pgvector is available on RDS PostgreSQL 15+)"
  type        = string
  default     = "16"
}

variable "db_multi_az" {
  description = "Run RDS in Multi-AZ (recommended for prod, doubles cost)"
  type        = bool
  default     = false
}

variable "db_deletion_protection" {
  description = "Protect the database from accidental deletion"
  type        = bool
  default     = false
}

# ---- edge / CI ---------------------------------------------------------------------------

variable "certificate_arn" {
  description = "Optional ACM certificate ARN. When set, the ALB serves HTTPS on 443 and redirects 80 -> 443."
  type        = string
  default     = ""
}

variable "github_repository" {
  description = "GitHub repo (owner/name) allowed to deploy via OIDC"
  type        = string
  default     = "argod2213/secure-rag-policy-engine"
}

variable "github_branch" {
  description = "Branch allowed to deploy"
  type        = string
  default     = "main"
}

variable "create_github_oidc_provider" {
  description = "Create the GitHub OIDC identity provider (set false if your account already has one)"
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "CloudWatch log retention"
  type        = number
  default     = 14
}
