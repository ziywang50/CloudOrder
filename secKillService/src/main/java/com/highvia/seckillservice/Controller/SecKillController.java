package com.highvia.seckillservice.Controller;
import com.highvia.common.events.SeckillConfirmedEvent;
import com.highvia.seckillservice.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

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

    //If there is enough items in stock then calculate else cancel the transaction
    /**
     * Lua script for atomic flash sale stock deduction.
     *
     * Operations performed atomically:
     * 1. Check if user already purchased (idempotency)
     * 2. Verify stock availability
     * 3. Deduct stock
     * 4. Record purchase in deduction history
     *
     * Return codes:
     *  1: Success
     *  0: Insufficient stock
     * -1: Product not found
     * -2: User already purchased (idempotent rejection)
     */
    String SEC_KILL_LUA_SCRIPT = """
        local product_deduction_history = KEYS[2]
        local exists = redis.call('hexists', product_deduction_history, ARGV[2])
        if (exists == 1) then
            return -2
        end
        local storageKey = KEYS[1]
        local stockQuantity = redis.call('GET', storageKey)
        if not stockQuantity then
            return -1
        end
        local quantity = tonumber(ARGV[1])
        if quantity <= tonumber(stockQuantity) then
            redis.call('DECRBY', storageKey, quantity)
            redis.call('HSET', product_deduction_history, ARGV[2], quantity)
            redis.call('EXPIRE', product_deduction_history, 86400)
            return 1
        else
            return 0
        end
    """;

    /**
     * Lua script for atomic stock rollback.
     *
     * Used when reservation fails after stock deduction.
     * Restores stock and removes deduction history entry.
     *
     * Return: [status, newStock]
     *  status 1: Rollback successful
     *  status -1: No deduction history found
     */
    String ROLLBACK_LUA_SCRIPT = """
        local storageKey = KEYS[1]
        local product_deduction_history = KEYS[2]
        local userId = ARGV[1]
        local quantity = redis.call('HGET', product_deduction_history, userId)
        if not quantity then
            return {-1, 0}
        end
        local newStock = redis.call('INCRBY', storageKey, tonumber(quantity))
        redis.call('HDEL', product_deduction_history, userId)
        return {1, newStock}
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
        Set<String> stockKeys = redisTemplate.keys("seckill:stock:*");
        List<Map<String, Object>> products = new ArrayList<>();
        if (stockKeys == null || stockKeys.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "products", Collections.emptyList()
            ));
        }
        for (String stockKey : stockKeys) {
            String productId = stockKey.replace("seckill:stock:", "");
            String stock = redisTemplate.opsForValue().get(stockKey);
            if (stock == null) {
                continue;
            }
            Map<Object, Object> activity = redisTemplate.opsForHash().entries("seckill:activity:" + productId);
            products.add(Map.of(
                    "productId", productId,
                    "stock", stock,
                    "startTime", activity.getOrDefault("startTime", ""),
                    "endTime", activity.getOrDefault("endTime", "")
            ));
        }
        return ResponseEntity.ok(Map.of("success", true, "products", products));
    }

    private ResponseEntity<SeckillResponse> createReservation(String userId, String productId, int quantity) {
        try {
            UUID reservationId = UUID.randomUUID();
            long expiresAt = System.currentTimeMillis() + RESERVATION_TIMEOUT_MINUTES * 60 * 1000;
            String reservationKey = "seckill:reservation:" + reservationId;
            redisTemplate.opsForHash().putAll(reservationKey, Map.of(
                    "userId", userId,
                    "productId", productId,
                    "quantity", String.valueOf(quantity)
            ));
            redisTemplate.expire(reservationKey, RESERVATION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            return ResponseEntity.ok(
                    new SeckillResponse(true, "Seckill Success", reservationId.toString(), expiresAt)
            );
        } catch (Exception e) {
            log.error("Failed to create reservation for product: {}", productId, e);
            return ResponseEntity.status(500).body(
                    new SeckillResponse(false, "System Error", null, null)
            );
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
        String activityKey = "seckill:activity:" + request.productId();
        Map<Object, Object> activity = redisTemplate.opsForHash().entries(activityKey);
        if (!activity.isEmpty()) {
            final int MILLISECONDS_TO_SECONDS = 1000;
            long now = System.currentTimeMillis() / MILLISECONDS_TO_SECONDS;
            long startTime = Long.parseLong(activity.get("startTime").toString());
            long endTime = Long.parseLong(activity.get("endTime").toString());

            if (now < startTime) {
                return ResponseEntity.ok(new SeckillResponse(false, "Seckill not started", null, null));
            }
            if (now > endTime) {
                return ResponseEntity.ok(new SeckillResponse(false, "Seckill finished", null, null));
            }
        }
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(SEC_KILL_LUA_SCRIPT, Long.class);
        String stockKey = "seckill:stock:" + request.productId();
        String productDeductionHistoryKey = "product:deduction:" + request.productId();
        String productDeductionHistoryField = userId;
        Long result = redisTemplate.execute(script,  Arrays.asList(stockKey, productDeductionHistoryKey), String.valueOf(request.quantity()), productDeductionHistoryField);
        String reservationKey = null;
        String pendingKey = null;
        String pendingValue = null;

        if (result != null && result == 1){
            try {
                log.info("Product: {} is available", request.productId());
                UUID reservationId = UUID.randomUUID();
                long expiresAt = System.currentTimeMillis() + RESERVATION_TIMEOUT_MINUTES * 60 * 1000;
                reservationKey = "seckill:reservation:" + reservationId;
                Map<String, String> reservationData = Map.of("userId", userId,
                        "productId", request.productId(),
                        "quantity", String.valueOf(request.quantity()));
                redisTemplate.opsForHash().putAll(reservationKey, reservationData);
                redisTemplate.expire(reservationKey, RESERVATION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
                pendingKey = "seckill:pending:" + request.productId() + ":" + userId;
                pendingValue = reservationId + ":" + expiresAt + ":" + request.quantity();
                redisTemplate.opsForList().rightPush(pendingKey, pendingValue);
                log.info("Added to pending list: product={}, reservation={}", request.productId(), reservationId);
                //test rollback function
                //if (true) throw new RuntimeException("TEST ROLLBACK");
                return ResponseEntity.ok(
                        new SeckillResponse(true, "Seckill Success", reservationId.toString(), expiresAt)
                );
            } catch (Exception e) {
                log.error("Failed to reserve seckill", e);
                try {
                    DefaultRedisScript<List> rollbackScript = new DefaultRedisScript<List>(ROLLBACK_LUA_SCRIPT, List.class);
                    //redisTemplate.opsForValue().increment(stockKey, request.quantity());
                    List<Long> rollbackResult = redisTemplate.execute(rollbackScript,  Arrays.asList(stockKey, productDeductionHistoryKey), productDeductionHistoryField);
                    if (rollbackResult.get(0) == 1) {
                        Long newStock = rollbackResult.get(1);
                        log.info("Stock rolled back for product: {}, new stock: {}", request.productId(), newStock);
                        //redisTemplate.opsForHash().delete(productDeductionHistoryKey, productDeductionHistoryField);
                        log.info("Product deduction history changed: {}", request.productId());
                    }
                    else {
                        log.info("Rollback Lua Script executed unsuccessfully");
                    }
                } catch (Exception e1) {
                    log.error("Failed to rollback for product: {}", request.productId(), e1);
                }
                if (reservationKey != null) {
                    try {
                        redisTemplate.delete(reservationKey);
                        log.info("Reservation key cleaned up: {}", reservationKey);
                    } catch (Exception e2) {
                        log.error("Failed to cleanup partial reservaion: {}", reservationKey);
                    }
                }
                if (pendingKey != null && pendingValue != null) {
                    try {
                        redisTemplate.opsForList().remove(pendingKey, 1, pendingValue);
                        log.info("Removed from pending list: {}", pendingValue);
                    } catch (Exception pendingError) {
                        log.error("Failed to cleanup pending list", pendingError);
                    }
                }
                return ResponseEntity.status(500).body(
                        new SeckillResponse(false, "System Error", null,null)
                );
            }
        } else {
            if (result != null && result == -2) {
                log.info("Product already reserved: {}. One user can only reserve one product at a time in one day", request.productId());
                return ResponseEntity.ok(
                        new SeckillResponse(false, "Product reserved", null, null)
                );
            }
            else if (result != null && result == -1) {
                String url = productServiceUrl + "/api/products/" + request.productId() + "/stock";
                try {
                    Integer stock = restTemplate.getForObject(url, Integer.class);
                    if (stock != null && stock > 0) {
                        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
                        result = redisTemplate.execute(script,
                                Arrays.asList(stockKey, productDeductionHistoryKey),
                                String.valueOf(request.quantity()), productDeductionHistoryField);
                        if (result != null && result == 1) {
                            return createReservation(userId, request.productId(), request.quantity());
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to restore stock from DB", e);
                }

                log.info("Can't find stock for this product: {}", request.productId());
                return ResponseEntity.ok(
                        new SeckillResponse(false, "Cannot find product", null, null)
                );
            }
            else {
                log.info("Stock deduction failed for product: {}", request.productId());
                return ResponseEntity.ok(
                        new SeckillResponse(false, "Seckill Failure", null, null)
                );
            }
        }
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
        //Check if reservationId exists
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(reservationKey))) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Reservation Id doenst exist"));
        }
        //Check reservation Data
        Map<Object, Object> reservationData = redisTemplate.opsForHash().entries(reservationKey);
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
            kafkaTemplate.send("seckill-events", secEvent);
            log.info("[KAFKA] Sent SeckillConfirmedEvent: {}", request.reservationId());

            //delete keys
            redisTemplate.delete(reservationKey);
            log.info("Reservation {} deleted", reservationKey);

            //delete pending key
            String pendingKey = "seckill:pending:" + productId + ":" + userId;
            List<String> pendingList = redisTemplate.opsForList().range(pendingKey, 0, -1);

            try {
                String productDeductionHistoryKey = "product:deduction:" + productId;
                String productDeductionHistoryField = userId;
                redisTemplate.opsForHash().delete(productDeductionHistoryKey, productDeductionHistoryField);
            } catch (Exception e) {
                log.error("Failed to delete product deduction history: {}", productId, e);
            }

            if (pendingList != null) {
                for (String pending : pendingList) {
                    if (pending.startsWith(request.reservationId() + ":")) {
                        redisTemplate.opsForList().remove(pendingKey, 1, pending);
                        log.info("Removed from pending list: {}", pending);
                        break;
                    }
                }
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


