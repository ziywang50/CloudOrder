package com.highvia.productservice.events;

import java.time.LocalDateTime;

public record ProductCreatedEvent(
        Long productId,
        String name,
        String description,
        Double price,
        Integer stock,
        LocalDateTime createdAt
) {

}
