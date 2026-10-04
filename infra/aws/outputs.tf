output "app_url" {
  description = "Public URL of the API"
  value       = "${local.https_enabled ? "https" : "http"}://${aws_lb.main.dns_name}"
}

output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "ecr_repository_name" {
  value = aws_ecr_repository.app.name
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}

output "ecs_task_family" {
  value = aws_ecs_task_definition.app.family
}

output "github_actions_role_arn" {
  description = "Set as the AWS_ROLE_ARN repository variable in GitHub"
  value       = aws_iam_role.github_deploy.arn
}

output "llm_api_key_secret_name" {
  description = "Put your OpenAI/OpenRouter key into this secret after apply"
  value       = aws_secretsmanager_secret.llm_api_key.name
}

output "admin_password_secret_name" {
  value = aws_secretsmanager_secret.admin_password.name
}

output "demo_password_secret_name" {
  value = aws_secretsmanager_secret.demo_password.name
}

output "db_endpoint" {
  value = aws_db_instance.main.address
}
