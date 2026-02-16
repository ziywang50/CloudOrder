package com.highvia.seckillservice.dto;

public record SeckillResponse(
        Boolean success,
        String message,
        String reservationId,  // Temporary reservation
        Long expiresAt
) {
}
