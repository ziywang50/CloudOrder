# Terraform for CloudOrder on ECS (multi-service)

locals {
  namespace_name = "${var.project_name}.local"

  services = {
    apiGateway = {
      service_name   = "api-gateway"
      container_port = 8085
      cpu            = 4096 #1024-2048
      memory         = 8192
      desired_count  = 4
      public_lb      = true
      env = [
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
        { name = "JAVA_TOOL_OPTIONS", value = "-Xmx4096m -Xms2048m -XX:MaxMetaspaceSize=512m" },
        { name = "REDIS_HOST", value = aws_elasticache_replication_group.redis.primary_endpoint_address },
        { name = "REDIS_PORT", value = tostring(aws_elasticache_replication_group.redis.port) },
        { name = "CUSTOMER_SERVICE_URL",      value = "http://customer-service.${local.namespace_name}:8088" },
        { name = "SECKILL_SERVICE_URL",       value = "http://seckill-service.${local.namespace_name}:8084" },
        { name = "ORDER_COMMAND_SERVICE_URL", value = "http://order-command-service.${local.namespace_name}:8090" },
        { name = "ORDER_QUERY_SERVICE_URL",   value = "http://order-query-service.${local.namespace_name}:8091" },
        { name = "PRODUCT_SERVICE_URL",       value = "http://product-service.${local.namespace_name}:8089" },
        { name = "CART_SERVICE_URL",          value = "http://cart-service.${local.namespace_name}:8083" },
        { name = "HTTPCLIENT_POOL_MAX_CONNECTIONS", value = "4000" },
      ]
      secrets = [
{ name = "JWT_PUBLIC_KEY", valueFrom = aws_secretsmanager_secret_version.jwt_public_key.arn }
	]
    }

    cartService = {
      service_name   = "cart-service"
      container_port = 8083
      cpu            = 1024
      memory         = 2048
      desired_count  = 3
      public_lb      = false
      env = [
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
        { name = "JAVA_TOOL_OPTIONS", value = "-Xmx1024m -Xms512m -XX:MaxMetaspaceSize=256m" },
        { name = "REDIS_HOST", value = aws_elasticache_replication_group.redis.primary_endpoint_address },
        { name = "REDIS_PORT", value = tostring(aws_elasticache_replication_group.redis.port) }
      ]
      secrets = []
    }

    customerService = {
      service_name   = "customer-service"
      container_port = 8088
      cpu            = 8192 #1024->8192
      memory         = 16384 #2048->16384
      desired_count  = 3
      public_lb      = false
      env = [
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
        { name = "JAVA_TOOL_OPTIONS", value = "-Xmx4096m -Xms2048m -XX:MaxMetaspaceSize=512m" },
        { name = "DATABASE_URL", value = aws_db_instance.customer.address },
        { name = "DATABASE_PORT", value = tostring(aws_db_instance.customer.port) },
        { name = "DATABASE_USERNAME", value = var.db_username }
      ]
      secrets = [
        {
          name      = "DATABASE_PASSWORD"
          valueFrom = aws_secretsmanager_secret_version.db_password.arn
        },
        {
          name      = "ADMIN_SECRET_KEY"
          valueFrom = aws_secretsmanager_secret_version.admin_secret.arn
        },
	{ name = "JWT_PRIVATE_KEY", valueFrom = aws_secretsmanager_secret_version.jwt_private_key.arn },
	{ name = "JWT_PUBLIC_KEY",  valueFrom = aws_secretsmanager_secret_version.jwt_public_key.arn }
      ]
    }

    orderCommandService = {
      service_name   = "order-command-service"
      container_port = 8090
      cpu            = 1024
      memory         = 2048
      desired_count  = 3
      public_lb      = false
      env = [
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
        { name = "JAVA_TOOL_OPTIONS", value = "-Xmx1024m -Xms512m -XX:MaxMetaspaceSize=256m" },
        { name = "DB_HOST", value = aws_db_instance.order_write.address },
        { name = "DB_PORT", value = tostring(aws_db_instance.order_write.port) },
        { name = "DB_NAME", value = "order_write_db" },
        { name = "DB_USER", value = var.db_username },
        { name = "KAFKA_BOOTSTRAP_SERVERS", value = aws_msk_cluster.kafka.bootstrap_brokers },
        { name = "CART_SERVICE_URL",    value = "http://cart-service.${local.namespace_name}:8083" },
        { name = "PRODUCT_SERVICE_URL", value = "http://product-service.${local.namespace_name}:8089" },
        { name = "REDIS_HOST",          value = aws_elasticache_replication_group.redis.primary_endpoint_address },
        { name = "REDIS_PORT",          value = tostring(aws_elasticache_replication_group.redis.port) }
      ]
      secrets = [
        {
          name      = "DB_PASSWORD"
          valueFrom = aws_secretsmanager_secret_version.db_password.arn
        }
      ]
    }

    orderQueryService = {
      service_name   = "order-query-service"
      container_port = 8091
      cpu            = 1024
      memory         = 2048
      desired_count  = 4
      public_lb      = false
      env = [
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
        { name = "JAVA_TOOL_OPTIONS", value = "-Xmx1024m -Xms512m -XX:MaxMetaspaceSize=256m" },
	{ name = "READ_DB_HOST_1", value = aws_db_instance.order_read_1.address },
	{ name = "READ_DB_HOST_2", value = aws_db_instance.order_read_2.address },
	{ name = "READ_DB_HOST_3", value = aws_db_instance.order_read_3.address },
        { name = "READ_DB_PORT_1", value = tostring(aws_db_instance.order_read_1.port) },
	{ name = "READ_DB_PORT_2", value = tostring(aws_db_instance.order_read_2.port) },
	{ name = "READ_DB_PORT_3", value = tostring(aws_db_instance.order_read_3.port) },
        { name = "READ_DB_NAME", value = "order_read_db" },
        { name = "READ_DB_USER", value = var.db_username },
        { name = "REDIS_HOST",              value = aws_elasticache_replication_group.redis.primary_endpoint_address },
        { name = "REDIS_PORT",              value = tostring(aws_elasticache_replication_group.redis.port) },
        { name = "KAFKA_BOOTSTRAP_SERVERS", value = aws_msk_cluster.kafka.bootstrap_brokers }
      ]
      secrets = [
        {
          name      = "READ_DB_PASSWORD"
          valueFrom = aws_secretsmanager_secret_version.db_password.arn
        }
      ]
    }

    productService = {
      service_name   = "product-service"
      container_port = 8089
      cpu            = 1024
      memory         = 2048
      desired_count  = 3
      public_lb      = false
      env = [
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
        { name = "JAVA_TOOL_OPTIONS", value = "-Xmx1024m -Xms512m -XX:MaxMetaspaceSize=256m" },
        { name = "ELASTICSEARCH_URL", value = "https://${aws_opensearch_domain.search.endpoint}" },
        { name = "KAFKA_BOOTSTRAP_SERVERS", value = aws_msk_cluster.kafka.bootstrap_brokers },
        { name = "DYNAMODB_ENDPOINT",       value = "https://dynamodb.${var.aws_region}.amazonaws.com" },
        { name = "AWS_REGION",              value = var.aws_region },
        { name = "REDIS_HOST",              value = aws_elasticache_replication_group.redis.primary_endpoint_address },
        { name = "REDIS_PORT",              value = tostring(aws_elasticache_replication_group.redis.port) }
      ]
      secrets = []
    }

    secKillService = {
      service_name   = "seckill-service"
      container_port = 8084
      cpu            = 2048
      memory         = 4096
      desired_count  = 6
      public_lb      = false
      env = [
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
        { name = "JAVA_TOOL_OPTIONS", value = "-Xmx2048m -Xms2048m -XX:MaxMetaspaceSize=256m -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -Xlog:gc*:stdout" },
        { name = "REDIS_HOST", value = aws_elasticache_replication_group.redis.primary_endpoint_address },
        { name = "REDIS_PORT", value = tostring(aws_elasticache_replication_group.redis.port) },
        { name = "KAFKA_BOOTSTRAP_SERVERS", value = aws_msk_cluster.kafka.bootstrap_brokers },
        { name = "PRODUCT_SERVICE_URL", value = "http://product-service.${local.namespace_name}:8089" },
        { name = "SERVER_TOMCAT_MAX_THREADS", value = "500" },
      ]
      secrets = []
    }
  }
}

# Network data

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

# us-west-2 default VPC has 4 subnets (one per AZ), so this resource is
# skipped at plan time (count = 0).  It exists as a safety net for custom
# VPCs that only cover 2 AZs: it adds a 3rd subnet in a genuinely distinct
# AZ so MSK brokers are spread across 3 AZs rather than doubling up.
resource "aws_subnet" "msk_extra_az" {
  count                   = length(data.aws_subnets.default.ids) < 3 ? 1 : 0
  vpc_id                  = data.aws_vpc.default.id
  cidr_block              = "172.31.32.0/20"
  availability_zone       = data.aws_availability_zones.available.names[2]
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.project_name}-msk-az3"
  }
}

locals {
  # Exactly 3 subnets, one per broker.  With the us-west-2 default VPC the
  # true branch always fires; the false branch handles custom 2-AZ VPCs.
  msk_subnets = length(data.aws_subnets.default.ids) >= 3 ? slice(tolist(data.aws_subnets.default.ids), 0, 3) : concat(slice(tolist(data.aws_subnets.default.ids), 0, 2), aws_subnet.msk_extra_az[*].id)
}

# ECS Cluster

resource "aws_ecs_cluster" "this" {
  name = "${var.project_name}-cluster"
}

# Service discovery namespace

resource "aws_service_discovery_private_dns_namespace" "this" {
  name = local.namespace_name
  vpc  = data.aws_vpc.default.id
}

# Security groups

resource "aws_security_group" "ecs" {
  name        = "${var.project_name}-ecs-sg"
  description = "ECS service ingress from VPC"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port       = 0
    to_port         = 0
    protocol        = "-1"
    cidr_blocks     = [data.aws_vpc.default.cidr_block]
    description     = "Allow VPC internal traffic"
  }

  ingress {
    from_port       = 8085
    to_port         = 8085
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
    description     = "Allow ALB to reach API Gateway"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "alb" {
  name        = "${var.project_name}-alb-sg"
  description = "ALB ingress"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Allow HTTP"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "data_services" {
  name        = "${var.project_name}-data-sg"
  description = "Data services ingress from ECS"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port       = 0
    to_port         = 0
    protocol        = "-1"
    security_groups = [aws_security_group.ecs.id]
    description     = "Allow ECS to access data services"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ECR repositories

resource "aws_ecr_repository" "service" {
  for_each             = local.services
  name                 = "${var.ecr_repository_prefix}/${each.value.service_name}"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

# Logs

resource "aws_cloudwatch_log_group" "service" {
  for_each          = local.services
  name              = "/ecs/${var.project_name}/${each.value.service_name}"
  retention_in_days = var.log_retention_days
}

# DynamoDB

resource "aws_dynamodb_table" "shopping_carts" {
  name         = "${var.project_name}-shopping-carts"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "cart_id"

  attribute {
    name = "cart_id"
    type = "S"
  }
}

# Table name must match @DynamoDBTable(tableName = "Products") in ProductEntity.java
resource "aws_dynamodb_table" "products" {
  name         = "Products"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "productId"

  attribute {
    name = "productId"
    type = "N"
  }
}

# Redis (ElastiCache)

resource "aws_elasticache_subnet_group" "redis" {
  name       = "${var.project_name}-redis"
  subnet_ids = data.aws_subnets.default.ids
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "${var.project_name}-redis"
  description                = "Redis primary + replica for CloudOrder"
  node_type                  = var.redis_node_type
  num_cache_clusters         = 2
  automatic_failover_enabled = true
  multi_az_enabled           = true
  parameter_group_name       = "default.redis7"
  port                       = 6379
  subnet_group_name          = aws_elasticache_subnet_group.redis.name
  security_group_ids         = [aws_security_group.data_services.id]
  snapshot_retention_limit   = 1
}

# RDS (Postgres)

resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db"
  subnet_ids = data.aws_subnets.default.ids
}

resource "aws_db_instance" "customer" {
  identifier             = "${var.project_name}-customer"
  engine                 = "postgres"
  engine_version         = "16"
  instance_class         = var.db_instance_class
  allocated_storage      = 20
  db_name                = "customerdb"
  username               = var.db_username
  password               = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.data_services.id]
  skip_final_snapshot    = true
  deletion_protection    = false
  publicly_accessible    = false
}

resource "aws_db_instance" "order_write" {
  identifier             = "${var.project_name}-order-write"
  engine                 = "postgres"
  engine_version         = "16"
  instance_class         = var.db_instance_class
  allocated_storage      = 20
  db_name                = "order_write_db"
  username               = var.db_username
  password               = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.data_services.id]
  skip_final_snapshot    = true
  deletion_protection    = false
  publicly_accessible    = false
}

resource "aws_db_instance" "order_read_1" {
  identifier             = "${var.project_name}-order-read-1"
  engine                 = "postgres"
  engine_version         = "16"
  instance_class         = var.db_instance_class
  allocated_storage      = 20
  db_name                = "order_read_db"
  username               = var.db_username
  password               = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.data_services.id]
  skip_final_snapshot    = true
  deletion_protection    = false
  publicly_accessible    = false
}

resource "aws_db_instance" "order_read_2" {
  identifier             = "${var.project_name}-order-read-2"
  engine                 = "postgres"
  engine_version         = "16"
  instance_class         = var.db_instance_class
  allocated_storage      = 20
  db_name                = "order_read_db"
  username               = var.db_username
  password               = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.data_services.id]
  skip_final_snapshot    = true
  deletion_protection    = false
  publicly_accessible    = false
}


resource "aws_db_instance" "order_read_3" {
  identifier             = "${var.project_name}-order-read-3"
  engine                 = "postgres"
  engine_version         = "16"
  instance_class         = var.db_instance_class
  allocated_storage      = 20
  db_name                = "order_read_db"
  username               = var.db_username
  password               = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.data_services.id]
  skip_final_snapshot    = true
  deletion_protection    = false
  publicly_accessible    = false
}

# MSK (Kafka)

resource "aws_msk_cluster" "kafka" {
  cluster_name           = "${var.project_name}-kafka"
  kafka_version          = "3.6.0"
  number_of_broker_nodes = 3

  broker_node_group_info {
    instance_type  = var.msk_instance_type
    client_subnets = local.msk_subnets
    security_groups = [aws_security_group.data_services.id]
    storage_info {       
      ebs_storage_info {
        volume_size = 100
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "PLAINTEXT"
      in_cluster    = true
    }
  }
}

# OpenSearch

resource "aws_opensearch_domain" "search" {
  domain_name    = "${var.project_name}-search"
  engine_version = "OpenSearch_2.11"

  cluster_config {
    instance_type          = var.opensearch_instance_type
    instance_count         = 1
    zone_awareness_enabled = false
  }

  ebs_options {
    ebs_enabled = true
    volume_size = 20
    volume_type = "gp3"
  }

  vpc_options {
    subnet_ids         = [tolist(data.aws_subnets.default.ids)[0]]
    security_group_ids = [aws_security_group.data_services.id]
  }

  access_policies = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = "*"
        Action = "es:*"
        Resource = "*"
      }
    ]
  })
}

# ECS Task Definitions

resource "aws_ecs_task_definition" "service" {
  for_each = local.services

  family                   = "${var.project_name}-${each.value.service_name}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = each.value.cpu
  memory                   = each.value.memory

  execution_role_arn = aws_iam_role.ecs_execution_role.arn
  task_role_arn      = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([
    {
      name      = each.value.service_name
      image     = "${aws_ecr_repository.service[each.key].repository_url}:latest"
      essential = true
      portMappings = [
        { containerPort = each.value.container_port }
      ]
      environment = each.value.env
      secrets     = each.value.secrets
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.service[each.key].name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "ecs"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:${each.value.container_port}/actuator/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 30
      }
    }
  ])
}

# Service discovery services

resource "aws_service_discovery_service" "service" {
  for_each = local.services

  name = each.value.service_name

  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.this.id

    dns_records {
      ttl  = 10
      type = "A"
    }

    routing_policy = "WEIGHTED"
  }

}

# Network Load Balancer for API Gateway

resource "aws_lb" "api" {
  name               = "${var.project_name}-api"
  load_balancer_type = "application"
  subnets            = data.aws_subnets.default.ids
  security_groups    = [aws_security_group.alb.id]
}

resource "aws_lb_target_group" "api" {
  name        = "${var.project_name}-api"
  port        = local.services.apiGateway.container_port
  protocol    = "HTTP"
  vpc_id      = data.aws_vpc.default.id
  target_type = "ip"

  health_check {
    path                = "/actuator/health"
    protocol            = "HTTP"
    matcher             = "200-399"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener" "api" {
  load_balancer_arn = aws_lb.api.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }
}

# ECS Services

resource "aws_ecs_service" "service" {
  for_each = local.services

  name            = each.value.service_name
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.service[each.key].arn
  desired_count   = each.value.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = data.aws_subnets.default.ids
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = true
  }

  service_registries {
    registry_arn = aws_service_discovery_service.service[each.key].arn
  }

  dynamic "load_balancer" {
    for_each = each.value.public_lb ? [1] : []
    content {
      target_group_arn = aws_lb_target_group.api.arn
      container_name   = each.value.service_name
      container_port   = each.value.container_port
    }
  }

  depends_on = [aws_lb_listener.api]

  lifecycle {
      ignore_changes = [desired_count]
  }
}

resource "aws_appautoscaling_target" "ecs" {
  for_each = local.services

  max_capacity       = 20
  min_capacity       = 1
  resource_id        = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.service[each.key].name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_cloudwatch_metric_alarm" "ecs_cpu_avg" {
  for_each = local.services

  alarm_name          = "${var.project_name}-${each.value.service_name}-cpu-max"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = 40
  alarm_description   = "Scale up when ANY ${each.value.service_name} instance CPU > 40%"

  dimensions = {
    ServiceName = aws_ecs_service.service[each.key].name
    ClusterName = aws_ecs_cluster.this.name
  }

  alarm_actions = [aws_appautoscaling_policy.ecs_cpu_avg[each.key].arn]
}

resource "aws_appautoscaling_policy" "ecs_cpu_avg" {
  for_each = local.services

  name               = "${each.value.service_name}-cpu-max-scaling"
  policy_type        = "StepScaling"
  resource_id        = aws_appautoscaling_target.ecs[each.key].resource_id
  scalable_dimension = aws_appautoscaling_target.ecs[each.key].scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs[each.key].service_namespace

  step_scaling_policy_configuration {
    adjustment_type = "ChangeInCapacity"
    cooldown        = 60

    step_adjustment {
      metric_interval_lower_bound = 0
      scaling_adjustment          = 2
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "ecs_cpu_low" {
  for_each = local.services

  alarm_name          = "${var.project_name}-${each.value.service_name}-cpu-low"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = 10
  alarm_description   = "Scale in when ${each.value.service_name} average CPU < 20%"

  dimensions = {
    ServiceName = aws_ecs_service.service[each.key].name
    ClusterName = aws_ecs_cluster.this.name
  }

  #alarm_actions = [aws_appautoscaling_policy.ecs_cpu_low[each.key].arn]
}

resource "aws_appautoscaling_policy" "ecs_cpu_low" {
  for_each = local.services

  name               = "${each.value.service_name}-cpu-low-scaling"
  policy_type        = "StepScaling"
  resource_id        = aws_appautoscaling_target.ecs[each.key].resource_id
  scalable_dimension = aws_appautoscaling_target.ecs[each.key].scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs[each.key].service_namespace

  step_scaling_policy_configuration {
    adjustment_type         = "ChangeInCapacity"
    cooldown                = 120

    step_adjustment {
      metric_interval_upper_bound = 0
      scaling_adjustment          = -1
    }
  }
}

# ==========================================
# Secrets Manager - Store Sensitive Data
# ==========================================

# 1. Create a secret for database password
resource "aws_secretsmanager_secret" "db_password" {
  name        = "${var.project_name}-db-password"
  description = "Database password for all RDS instances"
  
  recovery_window_in_days = 0
}

# 2. Put the actual password value into the secret
resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = var.db_password  # 从variables.tf读取
}

# 3. Create a secret for admin secret key
resource "aws_secretsmanager_secret" "admin_secret" {
  name        = "${var.project_name}-admin-secret"
  description = "Admin secret key for authentication"
  
  recovery_window_in_days = 0
}

# 4. Put the admin secret value
resource "aws_secretsmanager_secret_version" "admin_secret" {
  secret_id     = aws_secretsmanager_secret.admin_secret.id
  secret_string = var.admin_secret_key
}

resource "aws_secretsmanager_secret" "jwt_private_key" {
  name                    = "${var.project_name}-jwt-private-key"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "jwt_private_key" {
  secret_id     = aws_secretsmanager_secret.jwt_private_key.id
  secret_string = var.jwt_private_key
}

resource "aws_secretsmanager_secret" "jwt_public_key" {
  name                    = "${var.project_name}-jwt-public-key"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "jwt_public_key" {
  secret_id     = aws_secretsmanager_secret.jwt_public_key.id
  secret_string = var.jwt_public_key
}

# ==========================================
# Locust Load Tester EC2
# ==========================================

data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
}

resource "aws_security_group" "locust" {
  name        = "${var.project_name}-locust-sg"
  description = "Locust load tester"
  vpc_id      = data.aws_vpc.default.id

  # SSH from your IP
  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Locust web UI
  ingress {
    description = "Locust UI"
    from_port   = 8089
    to_port     = 8089
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Ping from within VPC
  ingress {
    description = "ICMP"
    from_port   = -1
    to_port     = -1
    protocol    = "icmp"
    cidr_blocks = [data.aws_vpc.default.cidr_block]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_iam_role" "locust" {
  name = "${var.project_name}-locust-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ec2.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "locust_ssm" {
  role       = aws_iam_role.locust.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "locust" {
  name = "${var.project_name}-locust-profile"
  role = aws_iam_role.locust.name
}

resource "aws_instance" "locust" {
  ami                  = data.aws_ami.amazon_linux_2023.id
  instance_type        = "c6i.2xlarge"
  subnet_id            = tolist(data.aws_subnets.default.ids)[0]
  vpc_security_group_ids = [aws_security_group.locust.id]
  iam_instance_profile = aws_iam_instance_profile.locust.name

  instance_market_options {
      market_type = "spot"
      spot_options {
        spot_instance_type = "one-time"
      }
    }

  # Install Python + Locust on first boot
  user_data = <<-EOF
              #!/bin/bash
              set -euxo pipefail
              dnf update -y
              dnf install -y python3 python3-pip tmux git
              pip3 install locust

              # Auto shutdown after 2 hours
              echo "shutdown -h +120" | at now
              EOF

  tags = {
    Name = "${var.project_name}-locust"
  }
}

# ==========================================
# Jaeger All-in-One (Distributed Tracing)
# ==========================================

resource "aws_security_group" "jaeger" {
  name        = "${var.project_name}-jaeger-sg"
  description = "Jaeger tracing ingress"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "Jaeger UI from anywhere"
    from_port   = 16686
    to_port     = 16686
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "OTLP gRPC from VPC"
    from_port   = 4317
    to_port     = 4317
    protocol    = "tcp"
    cidr_blocks = [data.aws_vpc.default.cidr_block]
  }

  ingress {
    description = "OTLP HTTP from VPC"
    from_port   = 4318
    to_port     = 4318
    protocol    = "tcp"
    cidr_blocks = [data.aws_vpc.default.cidr_block]
  }

  ingress {
    description = "Jaeger UI from VPC"
    from_port   = 16686
    to_port     = 16686
    protocol    = "tcp"
    cidr_blocks = [data.aws_vpc.default.cidr_block]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_ecs_task_definition" "jaeger" {
  family                   = "${var.project_name}-jaeger"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 512
  memory                   = 1024

  execution_role_arn = aws_iam_role.ecs_execution_role.arn

  container_definitions = jsonencode([
    {
      name      = "jaeger"
      image     = "jaegertracing/all-in-one:latest"
      essential = true
      portMappings = [
        { containerPort = 16686 },
        { containerPort = 4317 },
        { containerPort = 4318 }
      ]
      environment = [
        { name = "COLLECTOR_OTLP_ENABLED", value = "true" }
      ]
    }
  ])
}

resource "aws_service_discovery_service" "jaeger" {
  name = "jaeger"

  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.this.id

    dns_records {
      ttl  = 10
      type = "A"
    }

    routing_policy = "WEIGHTED"
  }
}

resource "aws_ecs_service" "jaeger" {
  name            = "jaeger"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.jaeger.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = data.aws_subnets.default.ids
    security_groups  = [aws_security_group.jaeger.id]
    assign_public_ip = true
  }

  service_registries {
    registry_arn = aws_service_discovery_service.jaeger.arn
  }
}

