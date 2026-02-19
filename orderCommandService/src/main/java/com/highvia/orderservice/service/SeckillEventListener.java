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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SeckillEventListener {
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OrderService orderService;
    private final RedisTemplate<String, String> redisTemplate;

    @KafkaListener(topics = "seckill-events", groupId = "order-command-service")
    public void handleSeckillConfirmed(SeckillConfirmedEvent event) {
        String reservationId = event.getReservationId();
        log.info("[SECKILL EVENT] Received: {}", reservationId);
        String processedKey = "seckill:order:processed:" + reservationId;
        try{
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(processedKey, "1", Duration.ofDays(7));
            if (Boolean.FALSE.equals(acquired)) {
                log.warn("[SECKILL] Duplicate event detected : {}", reservationId);
                return;
            }
            OrderEntity order = new OrderEntity();
            order.setUserId(Long.parseLong(event.getUserId()));
            order.setStatus("PENDING");
            order.setBuyerAddress(event.getAddress());
            ProductDTO product = orderService.getProduct(Long.parseLong(event.getProductId()));
            if (product == null) {
                log.error("Product not found");
                throw new RuntimeException("Product not found");
            }
            OrderItem orderitem = new OrderItem();
            orderitem.setProductId(Long.parseLong(event.getProductId()));
            orderitem.setProductName(product.productName());
            orderitem.setProductPrice(product.price());
            orderitem.setQuantity(event.getQuantity());
            order.addItem(orderitem);
            //add
            order.setBuyerName(event.getBuyerName());
            order.setBuyerPhone(event.getBuyerPhone());
            order.calculateTotal();
            OrderEntity savedOrder = orderRepository.save(order);
            log.info("[SECKILL] Order saved: {}", savedOrder.getOrderId());
            //add order created event
            OrderCreatedEvent orderEvent = orderService.convertToEvent(savedOrder);
            kafkaTemplate.send("order-events", orderEvent);
            log.info("[KAFKA] Sent OrderCreatedEvent: {}", savedOrder.getOrderId());

            //add stock deduction event
            List<StockDeductionRequest.StockItem> stockItems = savedOrder.getItems().stream().map(item-> new StockDeductionRequest.StockItem(item.getProductId(), item.getQuantity())).collect(Collectors.toList());
            StockDeductionRequest stockDeductionRequest = new StockDeductionRequest(savedOrder.getOrderId(), stockItems);
            kafkaTemplate.send("stock-deduction-requests", stockDeductionRequest);
            log.info("[SAGA] Stock deduction requested: {}", savedOrder.getOrderId());
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
}
