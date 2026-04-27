package com.highvia.seckillservice.Controller;
import com.highvia.common.events.SeckillConfirmedEvent;
import com.highvia.seckillservice.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PreDestroy;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;

//Java storage redis set. Ignore storeId for now for simplified design
/* seckill:storage:{productId} = {"5"}
WRONG---Redis is single threaded only one value is needed
 way1:
   seckill:order:{productId} - we could use hash for timestamp, and assume the hash is sorted in ascending order timestamp
 = {timestamp1: "quantity1", timestamp2: "quantity2", timestamp3: "quantity3"}
 way2:
    seckill:order:{timestamp}
 {product1: product2: product3:}
 Format should be {productId: number of items in stock}
 ---*/
//{

/* Redis is single-threaded, so no need to loop all products. no need for timestamp because redis will automatically order by timestamp
stockKey/storageKey: redis:stock:{productId}
* */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

/**
 * Flash Sale (Seckill) Controller
 *
 * Handles high-concurrency flash sale operations including:
 * - Listing available flash sale products
 * - Processing flash sale purchase requests with atomic inventory deduction
 * - Order confirmation and Kafka event publishing
 *
 * This controller implements production-grade patterns for handling
 * flash sale scenarios with thousands of concurrent requests.
 *
 * Key features:
 * - Lua scripts for atomic Redis operations (prevents overselling)
 * - Idempotency via deduction history (one purchase per user per product per day)
 * - Reservation system with timeout cleanup
 * - Automatic rollback on failure
 */
@RestController
@RequestMapping("api/seckill")
@RequiredArgsConstructor
@Slf4j
public class SecKillController {
    //redis
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate;

    @Value("${services.product.url}")
    private String productServiceUrl;

    // Reservation timeout (15 minutes)
    private static final long RESERVATION_TIMEOUT_MINUTES = 15;
    private static final int N_THREADS = 4;
    private final ExecutorService cleanupExecutor = Executors.newFixedThreadPool(N_THREADS);

    @PreDestroy
    public void shutdown() {
        cleanupExecutor.shutdown();
    }

    //If there is enough items in stock then calculate else cancel the transaction
    /**
     * Lua script for atomic flash sale stock deduction.
     *
     * All operations execute atomically in a single Redis round-trip:
     * 1. Validate activity exists and is within time window
     * 2. Check user purchase idempotency
     * 3. Verify stock availability
     * 4. Deduct stock
     * 5. Record deduction history (with 24h TTL)
     * 6. Create reservation (cleaned up by scheduled task)
     *
     * KEYS:
     *   [1] seckill:stock:{productId}       - stock counter (String)
     *   [2] product:deduction:{productId}    - purchase history (Hash)
     *   [3] seckill:activity:{productId}    - activity config (Hash)
     *   [4] seckill:reservation:{uuid}      - reservation data (Hash)
     *
     * ARGV:
     *   [1] quantity                        - items to purchase
     *   [2] userId                          - buyer identifier
     *   [3] now                             - current unix timestamp (seconds)
     *   [4] reservationTTL                  - reservation TTL (seconds, stored in reservation for scheduler use)
     *   [5] productId                       - product identifier
     *
     * Return codes:
     *   1: Success - stock deducted, reservation created
     *   0: Insufficient stock
     *  -1: Product not found (activity or stock missing)
     *  -2: User already purchased (idempotent rejection)
     *  -3: Seckill not started
     *  -4: Seckill ended
     */
    private static final String SEC_KILL_LUA_SCRIPT = """
        local storageKey = KEYS[1]
        local deductionHistoryKey = KEYS[2]
        local activityKey = KEYS[3]
        local reservationKey = KEYS[4]
        local quantity = tonumber(ARGV[1])
        local userId = ARGV[2]
        local now = tonumber(ARGV[3])
        local reservationTTL = tonumber(ARGV[4])
        local productId = ARGV[5]
        
        local startTime = redis.call('HGET', activityKey, 'startTime')
        if not startTime then return -1 end
        local endTime = redis.call('HGET', activityKey, 'endTime')
        if not endTime then return -1 end
        local expiresAt = now + reservationTTL
        if now < tonumber(startTime) then return -3 end
        if now > tonumber(endTime) then return -4 end
        
        if redis.call('HEXISTS', deductionHistoryKey, userId) == 1 then return -2 end
        
        local stockQuantity = redis.call('GET', storageKey)
        if not stockQuantity then return -1 end
        if quantity > tonumber(stockQuantity) then return 0 end
        
        redis.call('DECRBY', storageKey, quantity)
        redis.call('HSET', deductionHistoryKey, userId, quantity)
        redis.call('EXPIRE', deductionHistoryKey, 86400)
        redis.call('HMSET', reservationKey, 'userId', userId, 'productId', productId, 'quantity', quantity, 'expiresAt', expiresAt)
        return 1
    """;

    /*
        Lua script for deleting keys while confirming order
     */
    private static final String CONFIRM_CLEANUP_LUA = """
                redis.call('DEL', KEYS[1])
                redis.call('HDEL', KEYS[2], ARGV[1])
                return 1
            """;
/*
    String SEC_KILL_LUA_SCRIPT_GROUP = """
            local unavailable_products = {}
            local success = 1
            for index=1 , #KEYS do
                local storageKey = KEYS[index]
                local stockQuantity = redis.call('GET', storageKey)
                local quantity = tonumber(ARGV[index])
                if (quantity > tonumber(stockQuantity)) then
                    table.insert(unavailable_products, storageKey)
                    success = 0
                end
            end
            if (success == 0) then
                return {0, table.concat(unavailable_products, ",")};
            else
                for index=1 , #KEYS do
                    redis.call('DECRBY', KEYS[index], ARGV[index])
                end
                    return {1, ''}
            end
           """;*/

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT = new DefaultRedisScript<>(SEC_KILL_LUA_SCRIPT, Long.class);
    private static final DefaultRedisScript<Long> CLEANUP_SCRIPT = new DefaultRedisScript<>(CONFIRM_CLEANUP_LUA, Long.class);

    /**
     * Retrieves all active flash sale products.
     *
     * Returns a list of products currently configured for flash sale,
     * including their available stock and activity time window.
     *
     * @return ResponseEntity containing:
     *         - success: Operation status
     *         - products: List of flash sale products with:
     *           - productId: Product identifier
     *           - stock: Current available stock
     *           - startTime: Activity start (Unix timestamp)
     *           - endTime: Activity end (Unix timestamp)
     */
    @GetMapping("/products")
    public ResponseEntity<?> getSeckillProducts() {
        List<String> productIds = new ArrayList<>();
        ScanOptions scanOptions = ScanOptions.scanOptions().match("seckill:stock:*").count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                String stockKey = cursor.next();
                productIds.add(stockKey.replace("seckill:stock:", ""));
            }
        }
        if (productIds.isEmpty()) {
            return ResponseEntity.ok(Map.of("success", true, "products", List.of()));
        }
        try {
            List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (String pid : productIds) {
                    connection.stringCommands().get(("seckill:stock:" + pid).getBytes());
                    connection.hashCommands().hGetAll(("seckill:activity:" + pid).getBytes());
                }
                return null;
            });
            List<Map<String, Object>> products = new ArrayList<>();
            for (int i = 0; i < productIds.size(); i++) {
                String stock = (String) results.get(i * 2);
                if (stock == null) continue;

                @SuppressWarnings("unchecked")
                Map<Object, Object> activity = (Map<Object, Object>) results.get(i * 2 + 1);

                products.add(Map.of(
                        "productId", productIds.get(i),
                        "stock", stock,
                        "startTime", activity.getOrDefault("startTime", ""),
                        "endTime", activity.getOrDefault("endTime", "")
                ));
            }
            return ResponseEntity.ok(Map.of("success", true, "products", products));
        } catch (Exception e) {
            log.error("Failed to fetch seckill products", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Failed to fetch products"));
        }
    }
/**
 * Processes a flash sale purchase request.
 *
 * This endpoint handles high-concurrency flash sale requests with:
 * 1. Activity time validation
 * 2. Atomic stock deduction via Lua script
 * 3. Reservation creation with TTL
 * 4. Automatic rollback on failure
 *
 * The reservation must be confirmed within RESERVATION_TIMEOUT_MINUTES
 * or it will be automatically cleaned up by the ReservationCleanupService.
 *
 * @param request SeckillRequest containing:
 *                - productId: Product to purchase
 *                - userId: User making the purchase
 *                - quantity: Number of units to purchase
 * @return SeckillResponse with:
 *         - success: true if reservation created
 *         - message: Status description
 *         - reservationId: UUID for order confirmation (if successful)
 *         - expiresAt: Reservation expiration timestamp (if successful)
 */
    @PostMapping("/seckill")
    public ResponseEntity<SeckillResponse> secKill(@RequestBody SeckillRequest request, @RequestHeader("X-User-Id") String userId){
        String stockKey = "seckill:stock:" + request.productId();
        String deductionKey = "product:deduction:" + request.productId();
        String activityKey = "seckill:activity:" + request.productId();
        UUID reservationId = UUID.randomUUID();
        String reservationKey = "seckill:reservation:" + reservationId;
        long expiresAt = System.currentTimeMillis() / 1000 + RESERVATION_TIMEOUT_MINUTES * 60;

        Long result = redisTemplate.execute(SECKILL_SCRIPT,
                Arrays.asList(stockKey, deductionKey, activityKey, reservationKey),
                String.valueOf(request.quantity()),
                userId,
                String.valueOf(System.currentTimeMillis() / 1000),
                String.valueOf(RESERVATION_TIMEOUT_MINUTES * 60),
                request.productId()
        );

        if (result == null) {
            return ResponseEntity.status(500).body(
                    new SeckillResponse(false, "Redis error", null, null));
        }

        return switch (result.intValue()) {
            case 1 -> {
                log.info("Seckill success: product={}, user={}, reservation={}",
                        request.productId(), userId, reservationId);
                yield ResponseEntity.ok(
                        new SeckillResponse(true, "Seckill Success", reservationId.toString(), expiresAt));
            }
            case 0 -> {
                log.info("Insufficient stock: product={}", request.productId());
                yield ResponseEntity.ok(
                        new SeckillResponse(false, "Insufficient stock", null, null));
            }
            case -1 -> {
                log.info("Product not found in seckill: product={}", request.productId());
                yield ResponseEntity.ok(
                        new SeckillResponse(false, "Not a seckill product", null, null));
            }
            case -2 -> {
                log.info("One user can only reserve one product at a time in one day. Already purchased: product={}, user={}", request.productId(), userId);
                yield ResponseEntity.ok(
                        new SeckillResponse(false, "Already purchased. ", null, null));
            }
            case -3 -> ResponseEntity.ok(
                    new SeckillResponse(false, "Seckill not started", null, null));
            case -4 -> ResponseEntity.ok(
                    new SeckillResponse(false, "Seckill finished", null, null));
            default -> ResponseEntity.status(500).body(
                    new SeckillResponse(false, "Unknown error", null, null));
        };
    }


    /**
     * Confirms a flash sale reservation and initiates order creation.
     *
     * This endpoint:
     * 1. Validates the reservation exists and belongs to the user
     * 2. Publishes SeckillConfirmedEvent to Kafka for OrderService
     * 3. Cleans up reservation and deduction history
     *
     * The actual order creation is handled asynchronously by OrderService
     * listening to the seckill-events Kafka topic.
     *
     * @param request ConfirmRequest containing:
     *                - reservationId: UUID from successful seckill
     *                - userId: User confirming the order
     *                - address: Shipping address
     *                - paymentMethod: Selected payment method
     *                - buyerName: Recipient name
     *                - buyerPhone: Contact phone number
     * @return ResponseEntity with confirmation status
     *
     * @apiNote Reservation must be confirmed before RESERVATION_TIMEOUT_MINUTES
     */
    @PostMapping("/confirm")
    public ResponseEntity<?> confirmOrder(@RequestBody ConfirmRequest request, @RequestHeader("X-User-Id") String userId) {
        String reservationKey = "seckill:reservation:" + request.reservationId();
        String address = request.address();
        String paymentMethod = request.paymentMethod();
        //Check reservation Data
        Map<Object, Object> reservationData = redisTemplate.opsForHash().entries(reservationKey);
        //Check if reservationId exists
        if (reservationData.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Reservation not found"));
        }
        long expiresAt = Long.parseLong(reservationData.get("expiresAt").toString());
        if (System.currentTimeMillis() / 1000 > expiresAt) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Reservation expired"));
        }
        String reservedUserId = reservationData.get("userId").toString();
        String productId = reservationData.get("productId").toString();
        String quantity = reservationData.get("quantity").toString();
        String buyerName = request.buyerName();
        String buyerPhone = request.buyerPhone();

        //check for user Id
        if (!userId.equals(reservedUserId)) {
            log.warn("User mismatch warning. User {} is confirming user {}'s reservation", userId, reservedUserId);
            return ResponseEntity.status(403).body(
                    Map.of("success", false, "message", "Not authorized to confirm order")
            );
        }
        //create and confirm order
        try{
            //String orderId = UUID.randomUUID().toString();
            log.info("Confirmed order for product: {}", productId);

            SeckillConfirmedEvent secEvent = new SeckillConfirmedEvent(request.reservationId(), userId, productId, Integer.parseInt(quantity), System.currentTimeMillis(), address, paymentMethod, buyerName, buyerPhone);
            try {
                kafkaTemplate.send("seckill-events", productId, secEvent).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[SECKILL] Kafka send failed for {}: {}",
                                request.reservationId(), ex.getMessage());
                    }
                    else {
                        cleanupExecutor.submit(() -> {
                            log.info("[KAFKA] Sent SeckillConfirmedEvent: {}", request.reservationId());

                            //delete keys — only reached if Kafka send confirmed
                            String deductionKey = "product:deduction:" + productId;
                            redisTemplate.execute(CLEANUP_SCRIPT,
                                    Arrays.asList(reservationKey, deductionKey), userId);
                            log.info("Reservation {} deleted", reservationKey);
                        });
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException("Failed to send seckill event: " + e.getMessage(), e);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "reservationId", request.reservationId(),

                    "message", "Seckill confirmed, order is being processed"
            ));

        } catch (Exception e) {
            log.error("Failed to confirm order", e);
            return ResponseEntity.status(500).body(
                    Map.of("success", false, "message", "Internal Server Error on confirming order")
            );
        }
        //return new ResponseEntity(reservationId, address, paymentMethod);
    }

}


