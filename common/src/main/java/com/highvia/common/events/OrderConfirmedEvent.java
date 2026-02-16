package com.highvia.common.events;

import java.time.LocalDateTime;
import java.util.List;

public record OrderConfirmedEvent(
        String orderId,
        LocalDateTime confirmedAt
) {
}

