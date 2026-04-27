package com.highvia.orderservice.dto;

public record OrderRequest(
        String buyerName,
        String buyerPhone,
        String buyerAddress,
        String idempotencyKey
) {
}
