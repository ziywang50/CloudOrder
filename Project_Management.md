### Phase 1: Basic Microservices (Week 1-2)
**Implemented**:
-  CustomerService (authentication)
-  ProductService (catalog)
-  OrderService (order processing)
-  Eureka service discovery
-  OpenFeign inter-service communication

**AWS Deployment** (See hw9 for details):
- ECS Fargate containers
- RDS PostgreSQL
- Application Load Balancer
- Terraform IaC
- Validated Simple APIs

**Milestone**: Successfully deployed to AWS, validated cloud deployment

---

### Phase 2: Architecture Enhancement (Week 3-4)
**Realized Limitations**:
- Single database bottleneck
- Read queries slow (JOINs on large datasets)
- No clear transaction boundaries

**Architectural Decisions**:
1. **Split Order service → OrderCommand + OrderQuery**
    - Reason: CQRS pattern for read scalability
    - Trade-off: Eventual consistency

2. **Separate Write DB and Read DB**
    - Write: Normalized PostgreSQL (ACID)
    - Read: Denormalized PostgreSQL (no JOINs)
    - Sync: Kafka events

3. **Add CartService**
    - Reason: Redis for session management
    - Trade-off: Additional service complexity
---

### Phase 3: Saga Pattern (Week 5-6)
**Problem Identified**:
- Distributed transactions (Order → Stock deduction)
- No 2PC available in microservices
- Need rollback on failure

**Solution: Saga Orchestration**
- OrderCommandService as orchestrator
- Kafka for async coordination
- Compensation logic for rollback

**Implementation Steps**:
1. Create event types:
    - `StockDeductionRequest`
    - `StockDeductionSuccess`
    - `StockDeductionFailed`
    - `OrderConfirmedEvent`
    - `OrderCancelledEvent`

2. Implement listeners:
    - ProductService: `StockDeductionListener`
    - OrderCommandService: Success/Failed handlers
    - OrderQueryService: `OrderEventListener`

3. Add compensation:
    - `rollbackStock()` in ProductService
    - `cancelOrder()` in OrderCommandService

**Challenges**:
- Kafka consumer group configuration
    - Fixed: `auto.offset.reset=latest`
- Idempotency handling
    - Solution: Order ID as deduplication key

---

### Phase 3: Product CQRS (Week 5-6)
**Enhancement**: Polyglot persistence

**Implementation**:
- Write model: DynamoDB (optimistic locking)
- Read model: Elasticsearch (search optimization)
- Sync: Dual-write in `deductStock()`

**Trade-off Decision**:
- Chose synchronous dual-write over async Kafka
- Reason: Simpler, immediate consistency
- Acceptable: Product updates infrequent

**Design Choice: Dual-Write vs Kafka**

Option A: Dual-Write (Chosen)
- Pro: Immediate consistency
- Pro: Simpler implementation
- Con: Coupling (both DBs in transaction)
- Justified: Product updates infrequent (<1% traffic)

Option B: Kafka Async (Considered)
- Pro: Decoupled
- Pro: Resilient to ES downtime
- Con: Eventual consistency
- Con: More complex (event handling)

**Decision:** Dual-write acceptable for product catalog
(Low update frequency, consistency preferred)

**Challenge**:
- LocalStack DynamoDB setup
    - Solution: Proper endpoint configuration
- Elasticsearch mapping
    - Fixed: Correct field types
- Kafka serialization issues (3 hours debugging)
    - Problem: Class path conflicts
    - Solution: Shared `common/events` module
- Database schema design
    - Challenge: Denormalization strategy
    - Solution: `itemsJson` field with JPA converters


---