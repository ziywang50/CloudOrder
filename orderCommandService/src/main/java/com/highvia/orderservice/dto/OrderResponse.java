package com.highvia.orderservice.dto;

import com.highvia.common.events.OrderItemDTO;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        String orderId,
        Long userId,
        Double totalAmount,
        String status,
        String buyerName,
        String buyerPhone,
        String buyerAddress,
        List<OrderItemDTO> items,
        LocalDateTime createdAt
) {
}

