variable "aws_region" {
  type    = string
  default = "us-west-2"
}

variable "project_name" {
  type    = string
  default = "cloudorder"
}

variable "ecr_repository_prefix" {
  type    = string
  default = "cloudorder"
}

variable "ecs_desired_count" {
  type    = number
  default = 1
}

variable "log_retention_days" {
  type    = number
  default = 7
}

variable "db_username" {
  type    = string
  default = "postgres"
}

variable "db_password" {
  type      = string
  sensitive = true
  default   = "change-me"
}

variable "db_instance_class" {
  type    = string
  default = "db.t3.small"
}

variable "admin_secret_key" {
  type    = string
  default = "change-me"
}

variable "redis_node_type" {
  type    = string
  default = "cache.t3.medium"
}

variable "opensearch_instance_type" {
  type    = string
  default = "t3.small.search"
}

variable "msk_instance_type" {
  type    = string
  default = "kafka.t3.small"
}

variable "jwt_private_key" {
  description = "JWT RSA private key"
  type        = string
  sensitive   = true
}

variable "jwt_public_key" {
  description = "JWT RSA public key"
  type        = string
  sensitive   = true
}
