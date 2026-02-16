package com.highvia.orderservice.dto;

public record CartItemDTO(
        Long productId,
        Integer quantity
) {
}
