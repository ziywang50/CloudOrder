package com.highvia.orderservice.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
public class OrderEntity {
    @Id
    private String orderId;

    @Column(nullable = false)
    private Long userId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<OrderItem> items = new ArrayList<>();

    @Column(length = 20)
    private String status;

    //payment(need to add paymentService)
    //private String paymentMethod;  // CREDIT_CARD, PAYPAL, etc.
    //private String paymentStatus;  // PENDING, COMPLETED, REFUNDED, CANCELLED
    private Double totalAmount;

    private String transactionId;
    //timestamp
    //shipping information
    @Column(nullable = false, length = 50)
    private String buyerName;
    @Column(nullable = false, length = 20)
    private String buyerPhone;
    @Column(nullable = false, length = 100)
    private String buyerAddress;

    // Timestamps
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        orderId = java.util.UUID.randomUUID().toString();
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "CREATED";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void calculateTotal() {
        this.totalAmount = items.stream()
                .mapToDouble(item -> {
                    if (item.getSubtotal() == null) {
                        return item.getProductPrice() * item.getQuantity();
                    }
                    return item.getSubtotal();
                })
                .sum();
    }
}
