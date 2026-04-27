package com.highvia.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockUpdatedEvent {
    private Long productId;
    private Integer newStock;
    private Long timestamp;
}
