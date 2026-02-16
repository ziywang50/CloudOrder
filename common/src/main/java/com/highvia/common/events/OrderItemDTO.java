package com.highvia.common.events;

public record OrderItemDTO(
        Long productId,
        String productName,
        Double productPrice,
        Integer quantity,
        Double subtotal
) {
}
