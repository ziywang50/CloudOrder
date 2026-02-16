package com.highvia.seckillservice.dto;

public record SeckillRequest(
        String productId,
        Integer quantity
) {
}
