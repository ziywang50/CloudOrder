package com.highvia.common.events;

import java.time.LocalDateTime;

public record OrderCancelledEvent(
        String orderId,
        LocalDateTime cancelledAt
) {
}
