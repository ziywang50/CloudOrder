package com.highvia.orderservice.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = @Index(name = "idx_outbox_processed", columnList = "processed"))
@Data
public class OutboxEvent {

    @Id
    private String id;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String aggregateId; // orderId — used as Kafka message key

    @Column(nullable = false)
    private String eventType; // fully qualified class name for deserialization

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload; // JSON-serialized event

    @Column(nullable = false)
    private boolean processed = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        id = UUID.randomUUID().toString();
        createdAt = LocalDateTime.now();
    }
}
