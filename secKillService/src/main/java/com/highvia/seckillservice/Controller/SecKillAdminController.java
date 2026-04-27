package com.highvia.seckillservice.Controller;

import com.highvia.seckillservice.dto.ProductDTO;
import com.highvia.seckillservice.dto.SetSeckillRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

/**
 * Admin Controller for Flash Sale (Seckill) Management
 *
 * Provides administrative endpoints for configuring flash sale products,
 * including inventory management and activity scheduling.
 *
 * All endpoints require ADMIN role authentication.
 */

@RestController
@RequestMapping("api/admin/seckill")
@RequiredArgsConstructor
@Slf4j
public class SecKillAdminController {
    //redis
    private final RedisTemplate<String, String> redisTemplate;
    private final RestTemplate restTemplate;

    @Value("${services.product.url}")
    private String productServiceUrl;

    // Safety-net reconciliation interval: 30 minutes (event-driven sync handles real-time updates)
    private static final int RECONCILIATION_INTERVAL_MS = 1800000;

    /**
     * Retrieves product information from ProductService.
     *
     * @param productId The unique identifier of the product
     * @return ProductDTO containing product details, or null if not found
     */
    public ProductDTO getProduct(String productId) {
        try {
            String url = productServiceUrl + "/api/products/" + productId;
            return restTemplate.getForObject(url, ProductDTO.class);
        } catch (Exception e) {
            log.error("Failed to get product {}: {}", productId, e.getMessage());
            return null;
        }
    }


    /**
     * Parses time input supporting multiple formats.
     *
     * Supported formats:
     * - Unix timestamp in seconds (10 digits): e.g., "1739145600"
     * - ISO 8601 datetime: e.g., "2025-02-10T00:00:00" or "2025-02-10T00:00:00Z"
     *
     * @param time The time string to parse
     * @return Unix timestamp in seconds
     * @throws IllegalArgumentException if format is invalid or milliseconds are provided
     */
    private long parseTime(String time) {
        try {
            long value = Long.parseLong(time);
            if (value > 9999999999L) {
                throw new IllegalArgumentException("Use seconds, not milliseconds");
            }
            return value;
        } catch (NumberFormatException e) {
            try {
                String normalized = time.endsWith("Z") ? time : time + "Z";
                return Instant.parse(normalized).getEpochSecond();
            } catch (Exception parseError) {
                throw new IllegalArgumentException("Invalid format: " + time);
            }
        }
    }

    /**
     * Configures a product for flash sale activity.
     *
     * This endpoint sets up a product for flash sale by:
     * 1. Validating the product exists in ProductService
     * 2. Setting the flash sale inventory in Redis
     * 3. Configuring the activity time window
     *
     * The flash sale inventory is separate from regular product inventory,
     * allowing for controlled allocation during high-demand events.
     *
     * @param request SetSeckillRequest containing:
     *                - productId: Product to configure for flash sale
     *                - stock: Number of units available for flash sale
     *                - startTime: Activity start time (Unix seconds or ISO 8601)
     *                - endTime: Activity end time (Unix seconds or ISO 8601)
     * @return ResponseEntity with operation result:
     *         - 200 OK: Flash sale product configured successfully
     *         - 400 Bad Request: Product not found or invalid time format
     *
     * @apiNote Requires ADMIN role. Existing configuration will be overwritten.
     */
    @PostMapping("/setProduct")
    public ResponseEntity<?> setSeckillProducts(@RequestBody SetSeckillRequest request, @RequestHeader("X-User-Id") String userId) {
        String productId = request.productId();
        ProductDTO product = getProduct(productId);
        if (product == null) {
            log.error("Failed to get product from productService");
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Product not found: " + productId
            ));
        }
        Integer stock = request.stock();
        String stockKey = "seckill:stock:" + productId;
        String activityKey = "seckill:activity:" + productId;
        String SET_PRODUCT_LUA = """
                redis.call('SET', KEYS[1], ARGV[1])
                redis.call("HMSET", KEYS[2], 'startTime', ARGV[2], 'endTime', ARGV[3])
                return 1
                """;
        long startTime, endTime;
        try {
            startTime = parseTime(request.startTime());
            endTime = parseTime(request.endTime());
        }
        catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid time format: " + e.getMessage() + ". Use seconds (10 digits) or ISO 8601"
            ));
        }
        redisTemplate.execute(
                new DefaultRedisScript<>(SET_PRODUCT_LUA, Long.class),
                Arrays.asList(stockKey, activityKey),
                String.valueOf(request.stock()),
                String.valueOf(startTime),
                String.valueOf(endTime)
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "productId", productId,
                "stock", stock,
                "message", "Seckill product created successfully"
        ));
    }

    private static final String SYNC_STOCK_LUA = """
        local stockKey = KEYS[1]
        local dbStock = tonumber(ARGV[1])
        local current = redis.call('GET', stockKey)
        if not current then return -1 end
        local currentStock = tonumber(current)
        if dbStock < currentStock then
            local delta = currentStock - dbStock
            redis.call('DECRBY', stockKey, delta)
            return 1
        end
        return 0
    """;

    private static final DefaultRedisScript<Long> SYNC_SCRIPT = new DefaultRedisScript<>(SYNC_STOCK_LUA, Long.class);

    @Scheduled(fixedRate = RECONCILIATION_INTERVAL_MS)
    public void syncStockFromDB() {
        ScanOptions scanOptions = ScanOptions.scanOptions().match("seckill:stock:*").count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                String stockKey = cursor.next();
                String productId = stockKey.replace("seckill:stock:", "");
                String url = productServiceUrl + "/api/products/" + productId + "/stock";
                try {
                    Integer stock = restTemplate.getForObject(url, Integer.class);
                    if (stock != null) {
                        Long result = redisTemplate.execute(SYNC_SCRIPT,
                                List.of(stockKey),
                                String.valueOf(stock));
                        if (result != null && result == 1) {
                            log.info("Reconciled stock for product {}: adjusted to {}", productId, stock);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to sync stock for product {}", productId, e);
                }
            }
        }
    }
}
