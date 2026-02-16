package com.highvia.seckillservice.Controller;

import com.highvia.seckillservice.dto.ProductDTO;
import com.highvia.seckillservice.dto.SetSeckillRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

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
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
        redisTemplate.opsForHash().putAll(activityKey, Map.of(
                "startTime", String.valueOf(startTime), "endTime", String.valueOf(endTime)
        ));
        log.info("Set seckill product: productId={}, stock={}", productId, stock);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "productId", productId,
                "stock", stock,
                "message", "Seckill product created successfully"
        ));
    }
}
