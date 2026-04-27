output "api_gateway_nlb_dns" {
  value = aws_lb.api.dns_name
}

output "ecr_repositories" {
  value = { for k, v in aws_ecr_repository.service : k => v.repository_url }
}

output "customer_db_endpoint" {
  value = aws_db_instance.customer.address
}

output "order_write_endpoint" {
  value = aws_db_instance.order_write.address
}

output "order_read_endpoint_1" {
  value = aws_db_instance.order_read_1.address
}

output "order_read_endpoint_2" {
  value = aws_db_instance.order_read_2.address
}

output "order_read_endpoint_3" {
  value = aws_db_instance.order_read_3.address
}


output "redis_primary_endpoint" {
  value = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "redis_reader_endpoint" {
  value = aws_elasticache_replication_group.redis.reader_endpoint_address
}

output "opensearch_endpoint" {
  value = aws_opensearch_domain.search.endpoint
}

output "msk_bootstrap_brokers" {
  value = aws_msk_cluster.kafka.bootstrap_brokers
}

output "dynamodb_table_name" {
  value = aws_dynamodb_table.shopping_carts.name
}

output "locust_public_ip" {
  value = aws_instance.locust.public_ip
}

output "locust_connect_command" {
  value = "ssh -o StrictHostKeyChecking=no ec2-user@${aws_instance.locust.public_ip}"
}

output "locust_ui_url" {
  value = "http://${aws_instance.locust.public_ip}:8089"
}