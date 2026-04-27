package com.highvia.productservice.service;

import com.highvia.common.events.StockDeductionFailed;
import com.highvia.common.events.StockDeductionRequest;
import com.highvia.common.events.StockDeductionSuccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockDeductionListener {
    private final ProductService productService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    @KafkaListener(topics = "stock-deduction-requests", groupId = "product-service", containerFactory = "stockDeductionListenerFactory")
    public void handleStockDeduction(StockDeductionRequest request) {
        log.info("[SAGA EVENT] Stock deduction request: orderId={}", request.orderId());

        String dedupKey = "stock:deduction:processed:" + request.orderId();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", Duration.ofHours(24));
        if (Boolean.FALSE.equals(acquired)) {
            log.warn("[IDEMPOTENCY] Duplicate stock deduction request, re-sending success: orderId={}", request.orderId());
            sendBlocking("stock-deduction-success", request.orderId(), new StockDeductionSuccess(request.orderId()));
            return;
        }

        boolean allSuccess = true;
        List<StockDeductionRequest.StockItem> deductedItems = new ArrayList<>();
        String failReason = "";

        try {
            for (StockDeductionRequest.StockItem item : request.items()) {
                boolean success = productService.deductStock(item.productId(), item.quantity());
                if (!success) {
                    allSuccess = false;
                    failReason = "Insufficient stock for product: " + item.productId();
                    log.warn("! {}", failReason);
                    break;
                }
                deductedItems.add(item);
                log.info(" Stock deducted: product={}, qty={}", item.productId(), item.quantity());
            }
            if (allSuccess) {
                sendBlocking("stock-deduction-success", request.orderId(), new StockDeductionSuccess(request.orderId()));
                log.info("[SAGA SUCCESS] Stock deduction succeeded: {}", request.orderId());
            } else {
                rollbackStock(deductedItems);
                deductedItems.clear();
                redisTemplate.delete(dedupKey);
                sendBlocking("stock-deduction-failed", request.orderId(), new StockDeductionFailed(request.orderId(), failReason));
                log.error(" [SAGA FAILED] Stock deduction failed: {}", request.orderId());
            }
        } catch (Exception e) {
            rollbackStock(deductedItems);
            redisTemplate.delete(dedupKey);
            sendBlocking("stock-deduction-failed", request.orderId(), new StockDeductionFailed(request.orderId(), e.getMessage()));
            log.error(" [SAGA ERROR] Exception: {}", e.getMessage());
        }
    }

    private void sendBlocking(String topic, String orderId, Object event) {
        try {
            kafkaTemplate.send(topic, orderId, event).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during Kafka send to " + topic, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send to " + topic + ": " + e.getMessage(), e);
        }
    }

    //rollback
    private void rollbackStock(List<StockDeductionRequest.StockItem> deductedItems) {
        log.warn("[SAGA ROLLBACK] Rolling back {} items", deductedItems.size());
        for (StockDeductionRequest.StockItem item : deductedItems) {
            try {
                productService.addStock(item.productId(), item.quantity());
                log.info("Stock restored: product={}, +{}",
                item.productId(), item.quantity());
            } catch (Exception e) {
                log.error(" Rollback failed for product {}: {}",
                        item.productId(), e.getMessage());
            }
        }
    }
}
