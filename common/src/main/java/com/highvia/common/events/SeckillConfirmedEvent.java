package com.highvia.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillConfirmedEvent {
    private String reservationId;
    private String userId;
    private String productId;
    private Integer quantity;
    private Long timestamp;
    private String address;
    private String paymentMethod;
    //add
    private String buyerName;
    private String buyerPhone;
}
