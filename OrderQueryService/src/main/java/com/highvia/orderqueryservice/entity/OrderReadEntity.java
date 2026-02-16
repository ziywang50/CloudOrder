package com.highvia.orderqueryservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders_read", indexes = {
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_user_created", columnList = "userId, createdAt")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderReadEntity {
    @Id
    private String orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Double totalAmount;

    @Column(nullable = false, length = 50)
    private String buyerName;

    @Column(nullable = false, length = 20)
    private String buyerPhone;

    @Column(nullable = false, length = 100)
    private String buyerAddress;

    // 去规范化：将订单项信息序列化为JSON存储，避免JOIN
    @Column(columnDefinition = "TEXT")
    private String itemsJson;

    // 冗余字段：订单项数量，用于快速查询
    @Column(nullable = false)
    private Integer itemCount;

    // 冗余字段：产品ID列表，逗号分隔，用于快速筛选
    @Column(length = 1000)
    private String productIds;

    // 时间戳
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 预计算字段：用于分页和排序优化
    @Column(nullable = false)
    private Integer yearMonth; // YYYYMM格式，用于快速按月查询
}