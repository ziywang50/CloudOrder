package com.highvia.productservice.service;
import com.highvia.productservice.document.ProductDocument;
import com.highvia.productservice.events.ProductCreatedEvent;
import com.highvia.productservice.repository.ProductElasticsearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductQueryService {
    private final ProductElasticsearchRepository elasticsearchRepository;

    @KafkaListener(topics = "product-events", groupId = "product-query-service")
    public void handleProductCreated(ProductCreatedEvent event) {
        log.info("Syncing product to Elasticsearch: {}", event.productId());
        ProductDocument document = ProductDocument.builder()
                .id(event.productId().toString())
                .productId(event.productId())
                .name(event.name())
                .description(event.description())
                .price(event.price())
                .stock(event.stock())
                .build();

        elasticsearchRepository.save(document);
        log.info("Product indexed in Elasticsearch: {}", event.productId());
    }

    public List<ProductDocument> searchProducts(String keyword) {
        return elasticsearchRepository.findByNameContaining(keyword);
    }

    public ProductDocument getProductById(Long id) {
        return elasticsearchRepository.findById(id.toString())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found in ES"
                ));
    }
}
