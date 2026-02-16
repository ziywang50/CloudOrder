package com.highvia.seckillservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
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
     *
     * This script ensures consistency when restoring stock for expired reservations:
     * 1. Retrieves the reserved quantity from deduction history
     * 2. Atomically increments the stock
     * 3. Removes the deduction history entry
     *
     * Using Lua guarantees these operations execute atomically, preventing
     * race conditions with concurrent seckill requests.
     *
     * KEYS[1]: Stock key (seckill:stock:{productId})
     * KEYS[2]: Deduction history key (product:deduction:{productId})
     * ARGV[1]: User ID (field in deduction history hash)
     *
     * Returns: [status, newStock]
     *   status 1: Restoration successful, newStock contains updated inventory
     *   status -1: No deduction history found (already cleaned or never existed)
     */
    String RESTORE_STOCK_LUA_SCRIPT =
            """
                local stockKey = KEYS[1]
                local product_deduction_history = KEYS[2]
                local product_deduction_history_field = ARGV[1]
                local quantity = redis.call('HGET', product_deduction_history, product_deduction_history_field)
                if not quantity then
                    return {-1, 0}
                end
                local newStock = redis.call('INCRBY', stockKey, tonumber(quantity))
                redis.call('HDEL', product_deduction_history, product_deduction_history_field)
                return {1, newStock}
            """;

     /**
      * Scheduled job for cleaning up expired flash sale reservations.
      *
      * Execution Details:
      * - Frequency: Every 60 seconds (configurable via fixedRate)
      * - Scan Pattern: All keys matching "seckill:pending:*"
      * - Thread Safety: Lua scripts ensure atomic operations
      *
      * Pending Entry Format: "{reservationId}:{expiresAt}:{quantity}"
      * Pending Key Format: "seckill:pending:{productId}:{userId}"
      *
      * Cleanup Logic:
      * 1. For each pending entry, check if reservation still exists
      * 2. If reservation expired or missing:
      *    a. Restore stock to inventory (atomic Lua operation)
      *    b. Remove deduction history entry
      *    c. Remove pending list entry
      * 3. Log summary of restored items
      */
      @Scheduled(fixedRate = 60000)
    public void cleanupExpiredReservations() {
        log.debug("Starting cleanup job");
        try {
            Set<String> pendingKeys = redisTemplate.keys("seckill:pending:*");
            if (pendingKeys == null || pendingKeys.isEmpty()) {
                log.debug("No pending keys found");
                return;
            }
            long now = System.currentTimeMillis();
            Integer totalCleaned = 0;
            Integer totalRestored = 0;
            for (String pendingKey: pendingKeys) {
                try {
                    List<String> pendingList = redisTemplate.opsForList().range(pendingKey, 0, -1);
                    if (pendingList == null || pendingList.isEmpty()) {
                        continue;
                    }
                    for (String pending : pendingList) {
                        try {
                            String[] parts = pending.split(":");
                            if (parts.length != 3) {
                                log.warn("Invalid pending format: {}", pending);
                                continue;
                            }
                            String reservationId = parts[0];
                            long expiresAt = Long.parseLong(parts[1]);
                            Integer quantity = Integer.parseInt(parts[2]);
                            String reservationKey = "seckill:reservation:" + reservationId;
                            Boolean exists = redisTemplate.hasKey(reservationKey);
                            //log.info("DEBUG: reservationId={}, exists={}, expiresAt={}, now={}",
                            //        reservationId, exists, expiresAt, now);
                            boolean shouldCleanup = !Boolean.TRUE.equals(exists) || now > expiresAt;
                            //log.info("DEBUG: shouldCleanup={}", shouldCleanup);
                            if (shouldCleanup) {
                                String idempotencyKey = pendingKey.replace("seckill:pending:", "");
                                String[] idempotencyKeyParts = idempotencyKey.split(":");
                                String productId = idempotencyKeyParts[0];
                                String userId = idempotencyKeyParts[1];
                                //I need to split the string into two so I can get productId and userId
                                if (!Boolean.TRUE.equals(exists)) { //extra if for defensive programming
                                    String stockKey = "seckill:stock:" + productId;
                                    /*Long newStock = redisTemplate.opsForValue().increment(stockKey, quantity);
                                    */
                                    DefaultRedisScript<List> script = new DefaultRedisScript<List>(RESTORE_STOCK_LUA_SCRIPT, List.class);
                                    String productDeductionHistoryKey = "product:deduction:" + productId;
                                    List<Long> result = redisTemplate.execute(script,  Arrays.asList(stockKey, productDeductionHistoryKey), userId);
                                    if (result != null && result.get(0) == 1) {
                                        Long newStock = result.get(1);
                                        totalRestored += quantity;
                                        log.info("Restored stock for expired reservation: {}, product: {}, quantity: {}, new stock: {}", reservationId, productId, quantity, newStock);
                                    }
                                    else {
                                        log.info("Rollback Lua Script executed unsuccessfully");
                                    }
                                }
                                redisTemplate.opsForList().remove(pendingKey, 1, pending);
                                totalCleaned++;
                            }
                        } catch (Exception e) {
                            log.error("Error processing pending entry: {}", pending, e);
                        }
                    }
                } catch (Exception listError){
                    log.error("Error processing pending list", listError);
                }
            }
            if (totalRestored > 0) {
                log.info("Cleanup completed: restored {} items from {} expired reservations",
                        totalRestored, totalCleaned);
            } else {
                log.debug("Cleanup completed: no expired reservations");
            }
        } catch (Exception e) {
            log.error("Fatal error in cleanup job", e);
        }
    }
}
