variable "project_name" {
  type        = string
  description = "Project name for resource naming"
}

variable "vpc_id" {
  type        = string
  description = "VPC ID where RDS will be deployed"
}

variable "subnet_ids" {
  type        = list(string)
  description = "List of subnet IDs for RDS"
}

variable "db_password" {
  type        = string
  description = "MySQL root password"
  sensitive   = true
  default     = "Password123!"
}