package com.highvia.seckillservice.dto;

public record SetSeckillRequest(
        String productId,
        Integer stock,
        String startTime,
        String endTime
) {
}
