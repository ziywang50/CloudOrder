package com.highvia.seckillservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Reservation Cleanup Service for Flash Sale System
 *
 * Handles automatic cleanup of expired or abandoned flash sale reservations.
 * This service is critical for inventory management, ensuring that reserved
 * but uncommitted stock is returned to the available pool.
 *
 * Background Process:
 * - Runs every 60 seconds via Spring @Scheduled
 * - Scans all pending reservations across all products
 * - Identifies expired or orphaned reservations
 * - Atomically restores stock using Lua scripts
 *
 * Cleanup Triggers:
 * 1. Reservation TTL expired (Redis key no longer exists)
 * 2. Reservation timeout exceeded (expiresAt timestamp passed)
 *
 * This prevents inventory "leaks" where stock is deducted but never
 * converted to actual orders, which is critical for flash sale scenarios
 * where inventory is limited and high-demand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationCleanupService {
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Lua script for atomic stock restoration.
     * <p>
     * This script ensures consistency when restoring stock for expired reservations:
     * 1. Retrieves the reserved quantity from deduction history
     * 2. Atomically increments the stock
     * 3. Removes the deduction history entry
     * <p>
     * Using Lua guarantees these operations execute atomically, preventing
     * race conditions with concurrent seckill requests.
     * <p>
     * [1]: Stock key (seckill:stock:{productId})
     * KEYS[2]: Deduction history key (product:deduction:{productId})
     * ARGV[1]: User ID (field in deduction history hash)
     * <p>
     * Returns: [status, newStock]
     * status 1: Restoration successful, newStock contains updated inventory
     * KEYS     *   status -1: No deduction history found (already cleaned or never existed)
     */
    private static final String RESTORE_STOCK_LUA_SCRIPT =
    """
        local stockKey = KEYS[1]
        local product_deduction_history = KEYS[2]
        local reservationKey = KEYS[3]
        local product_deduction_history_field = ARGV[1]
        local quantity = redis.call('HGET', product_deduction_history, product_deduction_history_field)
        if not quantity then
            return {-1, 0}
        end
        local newStock = redis.call('INCRBY', stockKey, tonumber(quantity))
        redis.call('HDEL', product_deduction_history, product_deduction_history_field)
        redis.call('DEL', reservationKey)
        return {1, newStock}
    """;

    private static final DefaultRedisScript<List> RESTORE_SCRIPT = new DefaultRedisScript<>(RESTORE_STOCK_LUA_SCRIPT, List.class);

    /**
     * Scheduled job for cleaning up expired flash sale reservations.
     * <p>
     * Execution Details:
     * - Frequency: Every 60 seconds (configurable via fixedRate)
     * - Scan Pattern: All keys matching "seckill:pending:*"
     * - Thread Safety: Lua scripts ensure atomic operations
     * <p>
     * Pending Entry Format: "{reservationId}:{expiresAt}:{quantity}"
     * Pending Key Format: "seckill:pending:{productId}:{userId}"
     * <p>
     * Cleanup Logic:
     * 1. For each pending entry, check if reservation still exists
     * 2. If reservation expired or missing:
     * a. Restore stock to inventory (atomic Lua operation)
     * b. Remove deduction history entry
     * c. Remove pending list entry
     * 3. Log summary of restored items
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredReservations() {
        log.debug("Starting cleanup job");
        long now = System.currentTimeMillis() / 1000;
        ScanOptions scanOptions = ScanOptions.scanOptions().match("seckill:reservation:*").count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
            if (!cursor.hasNext()) {
                log.debug("No reservation keys found");
                return;
            }
            while (cursor.hasNext()) {
                String reservationKey = cursor.next();
                try {
                    Map<Object, Object> reservationData = redisTemplate.opsForHash().entries(reservationKey);
                    if (reservationData.isEmpty()) continue;
                    long expiresAt = Long.parseLong(reservationData.get("expiresAt").toString());
                    if (now <= expiresAt) continue;
                    String productId = reservationData.get("productId").toString();
                    String userId = reservationData.get("userId").toString();
                    String storageKey = "seckill:stock:" + productId;
                    String deductionKey = "product:deduction:" + productId;

                    List<Long> result = redisTemplate.execute(RESTORE_SCRIPT,
                            Arrays.asList(storageKey, deductionKey, reservationKey),
                            userId);

                    if (result != null && result.get(0) == 1) {
                        Long newStock = result.get(1);
                        log.info("Cleaned expired reservation: product={}, user={}, newStock={}", productId, userId, newStock);
                    }

                } catch (Exception e) {
                    log.error("Error processing reservation: {}", reservationKey, e);
                }
            }
        } catch (Exception e) {
            log.error("Fatal error in cleanup job", e);
        }
    }
}

