package com.highvia.seckillservice.dto;

public record ConfirmRequest(
        String reservationId,
        String userId,
        String address,
        String paymentMethod,
        String buyerName,   //add
        String buyerPhone
) {
}
