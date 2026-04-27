package com.highvia.orderservice.service;

import com.highvia.common.events.OrderCreatedEvent;
import com.highvia.common.events.SeckillConfirmedEvent;
import com.highvia.common.events.StockDeductionRequest;
import com.highvia.orderservice.dto.ProductDTO;
import com.highvia.orderservice.entity.OrderEntity;
import com.highvia.orderservice.entity.OrderItem;
import com.highvia.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SeckillEventListener {
    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final OrderService orderService;
    private final RedisTemplate<String, String> redisTemplate;
    private final TransactionTemplate transactionTemplate;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000),
            autoCreateTopics = "false",
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = "seckill-events", groupId = "order-command-service")
    public void handleSeckillConfirmed(SeckillConfirmedEvent event) {
        String reservationId = event.getReservationId();
        log.info("[SECKILL EVENT] Received: {}", reservationId);
        String processedKey = "seckill:order:processed:" + reservationId;

        // 1. Redis dedup — outside transaction
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(processedKey, "1", Duration.ofDays(7));
        if (Boolean.FALSE.equals(acquired)) {
            log.warn("[SECKILL] Duplicate event detected : {}", reservationId);
            return;
        }

        // 2. HTTP call — outside transaction, DB connection not yet held
        ProductDTO product;
        try {
            product = orderService.getProduct(Long.parseLong(event.getProductId()));
        } catch (Exception e) {
            redisTemplate.delete(processedKey);
            throw new RuntimeException("Failed to fetch product for seckill: " + reservationId, e);
        }
        if (product == null) {
            log.error("Product not found");
            redisTemplate.delete(processedKey);
            throw new RuntimeException("Product not found for seckill: " + reservationId);
        }

        // 3. DB work — inside transaction via TransactionTemplate
        try {
            ProductDTO finalProduct = product;
            transactionTemplate.executeWithoutResult(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int txStatus) {
                        if (txStatus == STATUS_ROLLED_BACK || txStatus == STATUS_UNKNOWN) {
                            redisTemplate.delete(processedKey);
                            log.warn("[SECKILL] Transaction rolled back or unknown, cleaned Redis key: {}", processedKey);
                        }
                    }
                });

                OrderEntity order = new OrderEntity();
                order.setUserId(Long.parseLong(event.getUserId()));
                order.setStatus("PENDING");
                order.setBuyerAddress(event.getAddress());

                OrderItem orderitem = new OrderItem();
                orderitem.setProductId(Long.parseLong(event.getProductId()));
                orderitem.setProductName(finalProduct.productName());
                orderitem.setProductPrice(finalProduct.price());
                orderitem.setQuantity(event.getQuantity());
                order.addItem(orderitem);
                order.setBuyerName(event.getBuyerName());
                order.setBuyerPhone(event.getBuyerPhone());
                order.calculateTotal();
                OrderEntity savedOrder = orderRepository.save(order);
                log.info("[SECKILL] Order saved: {}", savedOrder.getOrderId());

                OrderCreatedEvent orderEvent = orderService.convertToEvent(savedOrder);
                outboxService.save("order-events", savedOrder.getOrderId(), orderEvent);
                log.info("[OUTBOX] Queued OrderCreatedEvent: {}", savedOrder.getOrderId());

                List<StockDeductionRequest.StockItem> stockItems = savedOrder.getItems().stream()
                        .map(item -> new StockDeductionRequest.StockItem(item.getProductId(), item.getQuantity()))
                        .collect(Collectors.toList());
                StockDeductionRequest stockDeductionRequest = new StockDeductionRequest(savedOrder.getOrderId(), stockItems);
                outboxService.save("stock-deduction-requests", savedOrder.getOrderId(), stockDeductionRequest);
                log.info("[SAGA] Stock deduction requested: {}", savedOrder.getOrderId());
            });
        } catch (Exception e) {
            log.error("[SECKILL EVENT] Error: {}", e.getMessage());
            try {
                redisTemplate.delete(processedKey);
                log.info("[SECKILL] Cleaned up Redis key for failed order: {}", reservationId);
            } catch (Exception redisError) {
                log.error("[SECKILL] Failed to cleanup Redis key: {}", processedKey, redisError);
            }
            throw new RuntimeException("Failed to process seckill order: " + reservationId, e);
        }
    }

    @DltHandler
    public void handleDlt(SeckillConfirmedEvent event, Exception exception) {
        String reservationId = event.getReservationId();
        log.error("[DLT] seckill-events dead letter: reservationId={}, error={}",
                reservationId, exception.getMessage());
        try {
            String processedKey = "seckill:order:processed:" + reservationId;
            redisTemplate.delete(processedKey);
            log.info("[DLT] Cleaned Redis key so manual replay is possible: {}", reservationId);
        } catch (Exception e) {
            log.error("[DLT] Failed to cleanup Redis key for: {}", reservationId, e);
        }
    }
}
