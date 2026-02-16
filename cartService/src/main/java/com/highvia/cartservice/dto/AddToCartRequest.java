package com.highvia.cartservice.dto;

public record AddToCartRequest(
        Long productId,
        Integer quantity
) {
}
