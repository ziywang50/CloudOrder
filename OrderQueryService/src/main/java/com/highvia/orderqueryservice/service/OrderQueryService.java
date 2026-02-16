package com.highvia.orderqueryservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highvia.orderqueryservice.dto.OrderItemReadDTO;
import com.highvia.orderqueryservice.dto.OrderReadDTO;
import com.highvia.orderqueryservice.entity.OrderReadEntity;
import com.highvia.orderqueryservice.repository.OrderReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderQueryService {

    private final OrderReadRepository orderReadRepository;
    private final ObjectMapper objectMapper;


    @Cacheable(value = "userOrders", key = "#userId", unless = "#result == null or #result.isEmpty()")
    public List<OrderReadDTO> getOrdersByUserId(Long userId) {
        log.info("Querying orders for user: {}", userId);
        List<OrderReadEntity> orders = orderReadRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return orders.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }


    @Cacheable(value = "userOrdersByStatus", key = "#userId + '_' + #status")
    public List<OrderReadDTO> getOrdersByUserIdAndStatus(Long userId, String status) {
        log.info("Querying orders for user: {} with status: {}", userId, status);
        List<OrderReadEntity> orders = orderReadRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        return orders.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }


    @Cacheable(value = "orderById", key = "#orderId")
    public Optional<OrderReadDTO> getOrderByIdAndUserId(String orderId, Long userId) {
        log.info("Querying order: {} for user: {}", orderId, userId);
        return orderReadRepository.findByOrderIdAndUserId(orderId, userId)
                .map(this::convertToDTO);
    }


    @Cacheable(value = "orderById", key = "#orderId")
    public Optional<OrderReadDTO> getOrderById(String orderId) {
        log.info("Querying order: {}", orderId);
        return orderReadRepository.findById(orderId)
                .map(this::convertToDTO);
    }


    public List<OrderReadDTO> getOrdersByUserIdAndMonth(Long userId, int year, int month) {
        log.info("Querying orders for user: {} in {}-{}", userId, year, month);
        Integer yearMonth = year * 100 + month;
        List<OrderReadEntity> orders = orderReadRepository.findByUserIdAndYearMonth(userId, yearMonth);
        return orders.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "userOrderStats", key = "#userId")
    public OrderStatsDTO getUserOrderStats(Long userId) {
        log.info("Getting order stats for user: {}", userId);
        long totalOrders = orderReadRepository.countByUserId(userId);
        long pendingOrders = orderReadRepository.countByUserIdAndStatus(userId, "PENDING");
        long confirmedOrders = orderReadRepository.countByUserIdAndStatus(userId, "CONFIRMED");

        return new OrderStatsDTO(totalOrders, pendingOrders, confirmedOrders);
    }

    private OrderReadDTO convertToDTO(OrderReadEntity entity) {
        try {
            // 反序列化订单项JSON
            List<OrderItemReadDTO> items = objectMapper.readValue(
                entity.getItemsJson(),
                new TypeReference<List<OrderItemReadDTO>>() {}
            );

            return new OrderReadDTO(
                entity.getOrderId(),
                entity.getUserId(),
                entity.getStatus(),
                entity.getTotalAmount(),
                entity.getBuyerName(),
                entity.getBuyerPhone(),
                entity.getBuyerAddress(),
                items,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
            );
        } catch (Exception e) {
            log.error("Error converting order to DTO: {}", entity.getOrderId(), e);
            throw new RuntimeException("Failed to convert order data", e);
        }
    }

    /**
     * 订单统计DTO
     */
    @lombok.Value
    public static class OrderStatsDTO {
        long totalOrders;
        long pendingOrders;
        long confirmedOrders;
    }
}