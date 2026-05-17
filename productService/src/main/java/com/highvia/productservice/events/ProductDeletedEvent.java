package com.highvia.productservice.events;

import java.time.LocalDateTime;

public record ProductDeletedEvent(
        Long productId,
        LocalDateTime deletedAt
) {
}
