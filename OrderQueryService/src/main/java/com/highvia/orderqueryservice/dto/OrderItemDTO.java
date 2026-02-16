package com.highvia.orderqueryservice.dto;

public record OrderItemDTO(
        Long productId,
        String productName,
        Double productPrice,
        Integer quantity,
        Double subtotal
) {
}
