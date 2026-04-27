package com.highvia.productservice.service;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.*;
import com.highvia.productservice.document.ProductDocument;
import com.highvia.productservice.entity.ProductEntity;
import com.highvia.common.events.StockUpdatedEvent;
import com.highvia.productservice.events.ProductCreatedEvent;
import com.highvia.productservice.repository.ProductElasticsearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final AtomicLong idGenerator = new AtomicLong(1);
    private final AmazonDynamoDB amazonDynamoDB;
    private final DynamoDBMapper dynamoDBMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ProductElasticsearchRepository productRepository;

    /*public ProductService(AmazonDynamoDB amazonDynamoDB,
                          DynamoDBMapper dynamoDBMapper) {
        this.amazonDynamoDB = amazonDynamoDB;
        this.dynamoDBMapper = dynamoDBMapper;
    }*/

    @PostConstruct
    public void init() {
        try {
            CreateTableRequest request = new CreateTableRequest()
                    .withTableName("Products")
                    .withKeySchema(
                            new KeySchemaElement("productId", KeyType.HASH)
                    )
                    .withAttributeDefinitions(
                            new AttributeDefinition("productId", ScalarAttributeType.N)
                    )
                    .withBillingMode(BillingMode.PAY_PER_REQUEST);

            amazonDynamoDB.createTable(request);
            System.out.println(" DynamoDB table created: Products");
        } catch (ResourceInUseException e) {
            System.out.println(" Table already exists: Products");
        } catch (Exception e) {
            System.err.println(" DynamoDB error: " + e.getMessage());
        }
    }

    public List<ProductEntity> getAllProducts() {
        return dynamoDBMapper.scan(ProductEntity.class, new DynamoDBScanExpression());
    }

    public ProductEntity getProduct(Long id) {
        try {
            ProductEntity product = dynamoDBMapper.load(ProductEntity.class, id);

            if (product == null) {
                System.out.println(" Product not found in DB, with id: " + id);
                //product = createMockProduct(id);
            }

            return product;
        } catch (Exception e) {
            System.out.println(" DynamoDB error, : " + e.getMessage());
            return null;
            //return createMockProduct(id);
        }
    }

    public boolean addStock(Long productId, Integer quantity) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                ProductEntity product = dynamoDBMapper.load(ProductEntity.class, productId);
                if (product == null) {
                    log.error("Product not found for rollback: {}", productId);
                    return false;
                }

                product.setStock(product.getStock() + quantity);
                dynamoDBMapper.save(product);
                syncToElasticsearch(product); //sync to ES

                log.info("Stock added: product={}, +{}", productId, quantity);
                kafkaTemplate.send("stock-updated", String.valueOf(productId),
                        new StockUpdatedEvent(productId, product.getStock(), System.currentTimeMillis()));
                return true;
            } catch (ConditionalCheckFailedException e) {
                log.warn("Version conflict on addStock, retry {}/{}", i + 1, maxRetries);

                if (i < maxRetries - 1) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return false;
    }

    public boolean deductStock(Long productId, Integer quantity) {
        int maxRetries = 3;

        for (int i = 0; i < maxRetries; i++) {
            try {
                ProductEntity product = dynamoDBMapper.load(ProductEntity.class, productId);

                if (product == null) {
                    log.error("Product not found: {}", productId);
                    return false;
                }

                if (product.getStock() < quantity) {
                    log.warn("Insufficient stock for {}: required={}, available={}",
                            productId, quantity, product.getStock());
                    return false;
                }

                product.setStock(product.getStock() - quantity);
                dynamoDBMapper.save(product);  // Optimistic Locking
                syncToElasticsearch(product); //sync to ES

                log.info("Stock deducted: product={}, quantity={}, remaining={}",
                        productId, quantity, product.getStock());
                kafkaTemplate.send("stock-updated", String.valueOf(productId),
                        new StockUpdatedEvent(productId, product.getStock(), System.currentTimeMillis()));
                return true;

            } catch (ConditionalCheckFailedException e) {
                log.warn("Optimistic lock conflict on product {}, retry {}/{}",
                        productId, i + 1, maxRetries);

                if (i < maxRetries - 1) {
                    try {
                        Thread.sleep(50 * (i + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            } catch (Exception e) {
                log.error("Error deducting stock: {}", e.getMessage());
                return false;
            }
        }

        log.error("Failed to deduct stock after {} retries", maxRetries);
        return false;
    }

    public ProductEntity createProduct(ProductEntity product) {
        if (product.getProductId() == null) {
            product.setProductId(System.currentTimeMillis());
        }
        dynamoDBMapper.save(product);

        log.info("product {} is created", product.getProductId());
        //send kafka event
        ProductCreatedEvent event = new ProductCreatedEvent(
                product.getProductId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                LocalDateTime.now()
        );
        kafkaTemplate.send("product-events", event);
        log.info("Product event sent to Kafka");


        return product;
    }

    public ProductEntity updateProduct(Long id, ProductEntity updated) {
        try {
            updated.setProductId(id);
            dynamoDBMapper.save(updated);

            ProductCreatedEvent event = new ProductCreatedEvent(
                    updated.getProductId(),
                    updated.getName(),
                    updated.getDescription(),
                    updated.getPrice(),
                    updated.getStock(),
                    LocalDateTime.now()
            );
            kafkaTemplate.send("product-events", event);

            log.info("Product updated: {}", id);
            return updated;

        } catch (ConditionalCheckFailedException e) {
            log.error("Version conflict on product {}", id);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Version conflict. Refetch and retry.");
        }
    }

    public void deleteProduct(Long id) {
        ProductEntity product = dynamoDBMapper.load(ProductEntity.class, id);

        if (product == null) {
            throw new RuntimeException("Product not found: " + id);
        }
        //ProductEntity product = new ProductEntity();
        //product.setProductId(id);
        dynamoDBMapper.delete(product);
        log.info("Product deleted: {}", id);
    }

    private void syncToElasticsearch(ProductEntity product) {
        try {
            ProductDocument doc = productRepository
                    .findById(product.getProductId().toString())
                    .orElse(null);

            if (doc != null) {
                doc.setStock(product.getStock());
                productRepository.save(doc);
                log.info("ES synced: product={}, stock={}",
                        product.getProductId(), product.getStock());
            } else {
                log.warn("Product not found in ES: {}", product.getProductId());
            }
        } catch (Exception e) {
            log.error("Failed to sync to ES: {}", e.getMessage());
        }
    }

}
