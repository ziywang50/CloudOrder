package com.highvia.common.events;

import java.time.LocalDateTime;
import java.util.List;

public record OrderCreatedEvent(
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
