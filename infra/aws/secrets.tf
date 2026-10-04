# Application secrets. Generated values are random; the LLM API key is created empty and filled in
# once by you (see README) so it never lives in Terraform code or state.

resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "random_password" "admin" {
  length  = 24
  special = false
}

resource "random_password" "demo" {
  length  = 16
  special = false
}

resource "aws_secretsmanager_secret" "jwt" {
  name                    = "${local.name}/jwt-secret"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id     = aws_secretsmanager_secret.jwt.id
  secret_string = random_password.jwt.result
}

resource "aws_secretsmanager_secret" "admin_password" {
  name                    = "${local.name}/admin-password"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "admin_password" {
  secret_id     = aws_secretsmanager_secret.admin_password.id
  secret_string = random_password.admin.result
}

resource "aws_secretsmanager_secret" "demo_password" {
  name                    = "${local.name}/demo-password"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "demo_password" {
  secret_id     = aws_secretsmanager_secret.demo_password.id
  secret_string = random_password.demo.result
}

resource "aws_secretsmanager_secret" "llm_api_key" {
  name                    = "${local.name}/llm-api-key"
  description             = "OpenAI / OpenRouter API key. Set the value manually after apply."
  recovery_window_in_days = 0
}
