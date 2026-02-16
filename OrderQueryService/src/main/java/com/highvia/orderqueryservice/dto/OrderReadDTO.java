package com.highvia.orderqueryservice.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderReadDTO {
    private String orderId;
    private Long userId;
    private String status;
    private Double totalAmount;
    private String buyerName;
    private String buyerPhone;
    private String buyerAddress;
    private List<OrderItemReadDTO> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}