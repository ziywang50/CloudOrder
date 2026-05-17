package com.highvia.productservice.service;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.*;
import com.highvia.productservice.document.ProductDocument;
import com.highvia.productservice.entity.ProductEntity;
import com.highvia.common.events.StockUpdatedEvent;
import com.highvia.productservice.events.ProductCreatedEvent;
import com.highvia.productservice.events.ProductDeletedEvent;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private static final String TABLE_NAME = "Products";
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
                    .withTableName(TABLE_NAME)
                    .withKeySchema(
                            new KeySchemaElement("productId", KeyType.HASH)
                    )
                    .withAttributeDefinitions(
                            new AttributeDefinition("productId", ScalarAttributeType.N)
                    )
                    .withBillingMode(BillingMode.PAY_PER_REQUEST);

            amazonDynamoDB.createTable(request);
            System.out.println(" DynamoDB table created: " + TABLE_NAME);
        } catch (ResourceInUseException e) {
            System.out.println(" Table already exists: " + TABLE_NAME);
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
        try {
            Map<String, AttributeValue> key = Map.of(
                    "productId", new AttributeValue().withN(String.valueOf(productId)));
            Map<String, AttributeValue> values = Map.of(
                    ":qty", new AttributeValue().withN(String.valueOf(quantity)),
                    ":one", new AttributeValue().withN("1"));

            UpdateItemRequest request = new UpdateItemRequest()
                    .withTableName(TABLE_NAME)
                    .withKey(key)
                    .withUpdateExpression("SET stock = stock + :qty, version = version + :one")
                    .withConditionExpression("attribute_exists(stock)")
                    .withExpressionAttributeValues(values)
                    .withReturnValues(ReturnValue.UPDATED_NEW);

            UpdateItemResult result = amazonDynamoDB.updateItem(request);
            int newStock = Integer.parseInt(result.getAttributes().get("stock").getN());

            syncToElasticsearch(productId, newStock);
            log.info("Stock added: product={}, +{}, new stock={}", productId, quantity, newStock);
            kafkaTemplate.send("stock-updated", String.valueOf(productId),
                    new StockUpdatedEvent(productId, newStock, System.currentTimeMillis()));
            return true;

        } catch (ConditionalCheckFailedException e) {
            log.error("Product not found for stock addition: {}", productId);
            return false;
        } catch (Exception e) {
            log.error("Error adding stock: {}", e.getMessage());
            return false;
        }
    }

    public boolean deductStock(Long productId, Integer quantity) {
        try {
            Map<String, AttributeValue> key = Map.of(
                    "productId", new AttributeValue().withN(String.valueOf(productId)));
            Map<String, AttributeValue> values = Map.of(
                    ":qty", new AttributeValue().withN(String.valueOf(quantity)),
                    ":one", new AttributeValue().withN("1"));

            UpdateItemRequest request = new UpdateItemRequest()
                    .withTableName(TABLE_NAME)
                    .withKey(key)
                    .withUpdateExpression("SET stock = stock - :qty, version = version + :one")
                    .withConditionExpression("attribute_exists(stock) AND stock >= :qty")
                    .withExpressionAttributeValues(values)
                    .withReturnValues(ReturnValue.UPDATED_NEW);

            UpdateItemResult result = amazonDynamoDB.updateItem(request);
            int newStock = Integer.parseInt(result.getAttributes().get("stock").getN());

            syncToElasticsearch(productId, newStock);
            log.info("Stock deducted: product={}, quantity={}, remaining={}",
                    productId, quantity, newStock);
            kafkaTemplate.send("stock-updated", String.valueOf(productId),
                    new StockUpdatedEvent(productId, newStock, System.currentTimeMillis()));
            return true;

        } catch (ConditionalCheckFailedException e) {
            log.warn("Deduct stock failed for product {} (not found or insufficient stock)", productId);
            return false;
        } catch (Exception e) {
            log.error("Error deducting stock: {}", e.getMessage());
            return false;
        }
    }

    public ProductEntity createProduct(ProductEntity product) {
        if (product.getProductId() == null) {
            product.setProductId(System.currentTimeMillis());
        }
        dynamoDBMapper.save(product);
        syncToElasticsearch(product);

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
            syncToElasticsearch(updated);

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
        try {
            productRepository.deleteById(id.toString());
            log.info("Product deleted from ES: {}", id);
        } catch (Exception e) {
            log.error("Failed to delete product from ES: {}", e.getMessage());
        }
        kafkaTemplate.send("product-deleted", String.valueOf(id),
                new ProductDeletedEvent(id, LocalDateTime.now()));
        log.info("Product deleted: {}", id);
    }

    private void syncToElasticsearch(ProductEntity product) {
        try {
            ProductDocument doc = productRepository
                    .findById(product.getProductId().toString())
                    .orElse(null);

            if (doc == null) {
                doc = ProductDocument.builder()
                        .id(product.getProductId().toString())
                        .productId(product.getProductId())
                        .build();
            }

            doc.setName(product.getName());
            doc.setDescription(product.getDescription());
            doc.setPrice(product.getPrice());
            doc.setStock(product.getStock());
            productRepository.save(doc);
            log.info("ES synced (full): product={}", product.getProductId());
        } catch (Exception e) {
            log.error("Failed to sync to ES: {}", e.getMessage());
        }
    }

    private void syncToElasticsearch(Long productId, Integer newStock) {
        try {
            ProductDocument doc = productRepository
                    .findById(productId.toString())
                    .orElse(null);

            if (doc != null) {
                doc.setStock(newStock);
                productRepository.save(doc);
                log.info("ES synced: product={}, stock={}", productId, newStock);
            } else {
                log.warn("Product not found in ES: {}", productId);
            }
        } catch (Exception e) {
            log.error("Failed to sync to ES: {}", e.getMessage());
        }
    }

}
