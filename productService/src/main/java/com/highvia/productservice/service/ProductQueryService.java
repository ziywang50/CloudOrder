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

import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductQueryService {
    private final ProductElasticsearchRepository elasticsearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

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

    public List<ProductDocument> fuzzySearchByName(String name) {
        Query query = NativeQuery.builder()
                .withQuery(q -> q.match(m -> m
                        .field("name")
                        .query(name)
                        .fuzziness("AUTO")))
                .build();

        return elasticsearchOperations.search(query, ProductDocument.class)
                .getSearchHits()
                .stream()
                .map(SearchHit::getContent)
                .toList();
    }

    public ProductDocument getProductById(Long id) {
        return elasticsearchRepository.findById(id.toString())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found in ES"
                ));
    }
}
