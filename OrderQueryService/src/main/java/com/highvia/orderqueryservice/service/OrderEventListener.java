package com.highvia.orderqueryservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.highvia.orderqueryservice.entity.OrderReadEntity;
import com.highvia.common.events.OrderCancelledEvent;
import com.highvia.common.events.OrderConfirmedEvent;
import com.highvia.common.events.OrderCreatedEvent;
import com.highvia.orderqueryservice.repository.OrderReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {
    private final OrderReadRepository orderReadRepository;
    private final ObjectMapper objectMapper;

    /**
     * 统一处理所有订单事件
     */
    @KafkaListener(topics = "order-events", groupId = "order-query-service", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void handleOrderEvent(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        log.info("[CQRS Read] Received event type: {}", event.getClass().getSimpleName());

        log.info("=== DEBUG: Received from Kafka ===");
        log.info("Actual class: {}", event.getClass().getName());
        log.info("Actual content: {}", event);
        log.info("Is OrderCreatedEvent? {}", event instanceof OrderCreatedEvent);
        log.info("Is ConsumerRecord? {}", event instanceof org.apache.kafka.clients.consumer.ConsumerRecord);

        try {
            if (event instanceof OrderCreatedEvent) {
                handleOrderCreated((OrderCreatedEvent) event);
            } else if (event instanceof OrderConfirmedEvent) {
                handleOrderConfirmed((OrderConfirmedEvent) event);
            } else if (event instanceof OrderCancelledEvent) {
                handleOrderCancelled((OrderCancelledEvent) event);
            } else {
                log.warn("[CQRS Read] Unknown event type: {}", event.getClass().getName());
            }
        } catch (Exception e) {
            log.error("[CQRS Read] Failed to process event", e);
        }
    }

    /**
     * 处理订单创建事件
     */
    private void handleOrderCreated(OrderCreatedEvent event) {
        log.info("[CQRS Read] OrderCreated: orderId={}", event.orderId());
        try {
            OrderReadEntity readEntity = new OrderReadEntity();
            readEntity.setOrderId(event.orderId());
            readEntity.setUserId(event.userId());
            readEntity.setStatus(event.status());
            readEntity.setTotalAmount(event.totalAmount());
            readEntity.setBuyerName(event.buyerName());
            readEntity.setBuyerPhone(event.buyerPhone());
            readEntity.setBuyerAddress(event.buyerAddress());

            String itemsJson = objectMapper.writeValueAsString(event.items());
            readEntity.setItemsJson(itemsJson);

            readEntity.setItemCount(event.items().size());
            readEntity.setProductIds(
                    event.items().stream()
                            .map(item -> item.productId().toString())
                            .collect(Collectors.joining(","))
            );

            readEntity.setCreatedAt(event.createdAt());
            readEntity.setUpdatedAt(event.createdAt());

            YearMonth ym = YearMonth.from(event.createdAt());
            readEntity.setYearMonth(ym.getYear() * 100 + ym.getMonthValue());

            orderReadRepository.save(readEntity);
            evictUserCache(event.userId());

            log.info("[CQRS Read] Order saved to read DB: {}", event.orderId());
        } catch (Exception e) {
            log.error("Failed to save order to read DB: {}", event.orderId(), e);
        }
    }

    /**
     * 处理订单确认事件
     */
    private void handleOrderConfirmed(OrderConfirmedEvent event) {
        log.info("[CQRS Read] OrderConfirmed: orderId={}", event.orderId());

        Optional<OrderReadEntity> optionalOrder = orderReadRepository.findById(event.orderId());
        if (optionalOrder.isPresent()) {
            OrderReadEntity order = optionalOrder.get();
            order.setStatus("CONFIRMED");
            order.setUpdatedAt(LocalDateTime.now());
            orderReadRepository.save(order);

            evictUserCache(order.getUserId());
            evictOrderCache(event.orderId());

            log.info("[CQRS Read] Order confirmed in read DB: {}", event.orderId());
        } else {
            log.warn("[CQRS Read] Order not found: {}", event.orderId());
        }
    }

    /**
     * 处理订单取消事件
     */
    private void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("[CQRS Read] OrderCancelled: orderId={}", event.orderId());

        Optional<OrderReadEntity> optionalOrder = orderReadRepository.findById(event.orderId());
        if (optionalOrder.isPresent()) {
            OrderReadEntity order = optionalOrder.get();
            order.setStatus("CANCELLED");
            order.setUpdatedAt(LocalDateTime.now());
            orderReadRepository.save(order);

            evictUserCache(order.getUserId());
            evictOrderCache(event.orderId());

            log.info("[CQRS Read] Order cancelled in read DB: {}", event.orderId());
        } else {
            log.warn("[CQRS Read] Order not found: {}", event.orderId());
        }
    }

    @CacheEvict(value = {"userOrders", "userOrdersByStatus", "userOrderStats"}, key = "#userId")
    public void evictUserCache(Long userId) {
        log.debug("Evicted cache for user: {}", userId);
    }

    @CacheEvict(value = "orderById", key = "#orderId")
    public void evictOrderCache(String orderId) {
        log.debug("Evicted cache for order: {}", orderId);
    }
}
