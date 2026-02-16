output "endpoint" {
  value = split(":", aws_db_instance.mysql.endpoint)[0]
  description = "RDS endpoint hostname without port"
}