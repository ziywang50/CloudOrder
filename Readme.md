# CloudShopping - Distributed E-commerce Platform

A production-ready microservices e-commerce platform demonstrating CQRS, Saga orchestration, and event-driven architecture, deployed on AWS ECS.

## Architecture

### Services (8 microservices)

| Service | Port | Description |
|---------|------|-------------|
| API Gateway | 8080 | Request routing, rate limiting |
| Order Command | 8090 | Write operations, Saga orchestration |
| Order Query | 8091 | CQRS read model, Redis caching |
| Product Service | 8089 | DynamoDB + Elasticsearch |
| Customer Service | 8088 | Authentication, JWT |
| Cart Service | 8083 | Redis-based shopping cart |
| Seckill Service | 8092 | Flash sale with Redis Lua scripts |
| Discovery Service | 8761 | Eureka service registry |

### Technology Stack

- **Framework**: Spring Boot 3.x, Spring Cloud
- **Messaging**: Apache Kafka
- **Databases**: PostgreSQL, DynamoDB, Elasticsearch, Redis
- **Deployment**: AWS ECS, Terraform
- **Testing**: Locust

### System Architecture
```
                    ┌─────────────────┐
                    │   API Gateway   │
                    │    (Port 8080)  │
                    └────────┬────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│ Order Command │   │ Product       │   │ Seckill       │
│ (8090)        │   │ (8089)        │   │ (8092)        │
└───────┬───────┘   └───────────────┘   └───────────────┘
        │                                        │
        │ Kafka Events                           │ Redis Lua
        ▼                                        ▼
┌───────────────┐                       ┌───────────────┐
│ Order Query   │                       │ Redis         │
│ (8091)        │                       │ Atomic Ops    │
└───────────────┘                       └───────────────┘
```

## Key Patterns

### 1. CQRS (Command Query Responsibility Segregation)

- **Write Model**: Normalized PostgreSQL with ACID transactions
- **Read Model**: Denormalized PostgreSQL + Redis caching
- **Sync**: Kafka event streaming
- **Result**: 93% latency reduction (700ms → 50ms P95)

### 2. Saga Orchestration
```
Order Created (PENDING)
    │
    ▼
Stock Deduction Request ──► Kafka
    │
    ▼
Product Service: Deduct Stock
    │
    ├── Success ──► Order CONFIRMED
    │
    └── Failed ──► Order CANCELLED (Compensation)
```

### 3. Flash Sale (Seckill)

- Redis Lua scripts for atomic inventory operations
- Idempotency controls prevent duplicate purchases
- Automatic rollback on failure
- Zero overselling under high concurrency

### 4. Polyglot Persistence

| Database | Use Case | Why |
|----------|----------|-----|
| PostgreSQL | Orders, Users | ACID transactions |
| DynamoDB | Product writes | High throughput |
| Elasticsearch | Product search | Full-text search |
| Redis | Cart, Cache, Seckill | Low latency |

## Quick Start

### Prerequisites
- Java 17+
- Docker & Docker Compose
- Gradle

### Local Development
```bash
# Start infrastructure
docker-compose up -d

# Start services
./gradlew bootRun  # Run in each service directory
```

### API Endpoints
```
POST /api/auth/register     # Register user
POST /api/auth/login        # Login, get JWT
GET  /api/products          # List products
POST /api/cart              # Add to cart
POST /api/orders            # Create order (triggers Saga)
GET  /api/orders/user/{id}  # Query orders (CQRS read)
POST /api/seckill/{id}      # Flash sale purchase
```

## Project Structure
```
CloudShopping/
├── api-gateway/            # Request routing
├── common/                 # Shared events & DTOs
├── customerService/        # User management
├── orderService/           # Order write (Saga)
├── orderQueryService/      # Order read (CQRS)
├── productService/         # Product catalog
├── cartService/            # Shopping cart
├── seckillService/         # Flash sale
├── discoveryService/       # Eureka server
├── locust_tests/           # Load testing scripts
├── terraform/              # AWS infrastructure
└── docker-compose.yml
```

## Deployment

### AWS Architecture

- **Compute**: ECS Fargate (auto-scaling)
- **Database**: RDS PostgreSQL, DynamoDB, ElastiCache
- **Messaging**: MSK (Managed Kafka)
- **Load Balancer**: Application Load Balancer

### Deploy with Terraform
```bash
cd terraform
terraform init
terraform plan
terraform apply
```

## Author

**Ziyue Wang** - Architecture design, full-stack development

## License

MIT
