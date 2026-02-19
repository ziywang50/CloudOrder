variable "aws_region" {
  type    = string
  default = "us-west-1"
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
  default = "db.t3.medium"
}

variable "admin_secret_key" {
  type    = string
  default = "change-me"
}

variable "redis_node_type" {
  type    = string
  default = "cache.m5.large"
}

variable "opensearch_instance_type" {
  type    = string
  default = "m5.large.search"
}

variable "msk_instance_type" {
  type    = string
  default = "kafka.m5.large"
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
