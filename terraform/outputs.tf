output "api_gateway_nlb_dns" {
  value = aws_lb.api.dns_name
}

output "ecr_repositories" {
  value = { for k, v in aws_ecr_repository.service : k => v.repository_url }
}

output "customer_db_endpoint" {
  value = aws_db_instance.customer.address
}

output "order_db_endpoint" {
  value = aws_db_instance.order.address
}

output "redis_endpoint" {
  value = aws_elasticache_cluster.redis.cache_nodes[0].address
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
