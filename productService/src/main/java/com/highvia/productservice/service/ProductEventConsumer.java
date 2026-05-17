package com.highvia.productservice.service;

import com.highvia.common.events.StockUpdatedEvent;
import com.highvia.productservice.document.ProductDocument;
import com.highvia.productservice.events.ProductCreatedEvent;
import com.highvia.productservice.events.ProductDeletedEvent;
import com.highvia.productservice.repository.ProductElasticsearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductEventConsumer {
    private final ProductElasticsearchRepository productRepository;

    @KafkaListener(topics = "product-events", groupId = "product-es-sync")
    public void handleProductEvent(ProductCreatedEvent event) {
        try {
            ProductDocument doc = productRepository
                    .findById(event.productId().toString())
                    .orElse(null);

            if (doc == null) {
                doc = ProductDocument.builder()
                        .id(event.productId().toString())
                        .productId(event.productId())
                        .build();
            }

            doc.setName(event.name());
            doc.setDescription(event.description());
            doc.setPrice(event.price());
            doc.setStock(event.stock());
            productRepository.save(doc);
            log.info("ES synced via Kafka: product={}", event.productId());
        } catch (Exception e) {
            log.error("Failed to sync product {} to ES: {}", event.productId(), e.getMessage());
            throw e;
        }
    }

    @KafkaListener(topics = "stock-updated", groupId = "product-es-sync")
    public void handleStockUpdated(StockUpdatedEvent event) {
        try {
            ProductDocument doc = productRepository
                    .findById(event.getProductId().toString())
                    .orElse(null);

            if (doc != null) {
                doc.setStock(event.getNewStock());
                productRepository.save(doc);
                log.info("ES stock synced via Kafka: product={}, stock={}",
                        event.getProductId(), event.getNewStock());
            } else {
                log.warn("Product not found in ES for stock sync: {}", event.getProductId());
            }
        } catch (Exception e) {
            log.error("Failed to sync stock for product {} to ES: {}",
                    event.getProductId(), e.getMessage());
            throw e;
        }
    }

    @KafkaListener(topics = "product-deleted", groupId = "product-es-sync")
    public void handleProductDeleted(ProductDeletedEvent event) {
        try {
            productRepository.deleteById(event.productId().toString());
            log.info("ES deleted via Kafka: product={}", event.productId());
        } catch (Exception e) {
            log.error("Failed to delete product {} from ES: {}", event.productId(), e.getMessage());
            throw e;
        }
    }
}
