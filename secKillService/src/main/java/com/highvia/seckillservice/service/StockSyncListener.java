package com.highvia.seckillservice.service;

import com.highvia.common.events.StockUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Listens for stock updates from ProductService and atomically
 * adjusts the seckill Redis stock to stay in sync with the DB.
 *
 * Only adjusts downward — if DB stock drops below Redis stock,
 * Redis is lowered to match. This prevents overselling when
 * stock is reduced through non-seckill channels.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockSyncListener {
    private final RedisTemplate<String, String> redisTemplate;

    // Atomically lower Redis stock to DB level if DB is lower.
    // Uses DECRBY with the delta to avoid overwriting concurrent seckill deductions.
    // Returns 1 if adjusted, 0 if no adjustment needed, -1 if key doesn't exist.
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

    @KafkaListener(topics = "stock-updated", containerFactory = "stockUpdatedListenerFactory")
    public void handleStockUpdated(StockUpdatedEvent event) {
        String stockKey = "seckill:stock:" + event.getProductId();

        Long result = redisTemplate.execute(SYNC_SCRIPT,
                List.of(stockKey),
                String.valueOf(event.getNewStock()));

        if (result != null && result == 1) {
            log.info("Seckill stock synced for product {}: set to {}",
                    event.getProductId(), event.getNewStock());
        }
    }
}
