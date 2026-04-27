package com.highvia.orderqueryservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highvia.orderqueryservice.entity.OrderReadEntity;
import com.highvia.common.events.OrderCancelledEvent;
import com.highvia.common.events.OrderConfirmedEvent;
import com.highvia.common.events.OrderCreatedEvent;
import com.highvia.orderqueryservice.repository.OrderReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
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
    private final CacheEvictionService cacheEvictionService;

    /**
     * 统一处理所有订单事件
     */
    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            autoCreateTopics = "false",
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = "order-events", groupId = "order-query-service", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void handleOrderEvent(ConsumerRecord<String, Object> record) throws JsonProcessingException {
        Object event = record.value();
        log.info("[CQRS Read] Received event type: {}", event.getClass().getSimpleName());

        if (event instanceof OrderCreatedEvent) {
            handleOrderCreated((OrderCreatedEvent) event);
        } else if (event instanceof OrderConfirmedEvent) {
            handleOrderConfirmed((OrderConfirmedEvent) event);
        } else if (event instanceof OrderCancelledEvent) {
            handleOrderCancelled((OrderCancelledEvent) event);
        } else {
            log.warn("[CQRS Read] Unknown event type: {}", event.getClass().getName());
        }
    }

    @DltHandler
    public void handleDlt(ConsumerRecord<String, Object> record, Exception exception) {
        log.error("[DLT] order-events dead letter: topic={}, partition={}, offset={}, error={}",
                record.topic(), record.partition(), record.offset(), exception.getMessage());
    }

    /**
     * 处理订单创建事件
     */
    private void handleOrderCreated(OrderCreatedEvent event) throws JsonProcessingException {
        log.info("[CQRS Read] OrderCreated: orderId={}", event.orderId());
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
        cacheEvictionService.evictUserCache(event.userId());

        log.info("[CQRS Read] Order saved to read DB: {}", event.orderId());
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

            cacheEvictionService.evictUserCache(order.getUserId());
            cacheEvictionService.evictOrderCache(event.orderId());

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

            cacheEvictionService.evictUserCache(order.getUserId());
            cacheEvictionService.evictOrderCache(event.orderId());

            log.info("[CQRS Read] Order cancelled in read DB: {}", event.orderId());
        } else {
            log.warn("[CQRS Read] Order not found: {}", event.orderId());
        }
    }

}
