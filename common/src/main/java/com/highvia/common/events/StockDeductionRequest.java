package com.highvia.common.events;

import java.util.List;

public record StockDeductionRequest(
        String orderId,
        List<StockItem> items
) {
    public record StockItem(
            Long productId,
            Integer quantity
    ) {}
}
