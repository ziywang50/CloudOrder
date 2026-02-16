package com.highvia.common.events;

public record StockDeductionFailed(
        String orderId,
        String reason
) {
}
