package com.highvia.orderservice.service;

import com.highvia.orderservice.dto.*;
import com.highvia.orderservice.entity.OrderEntity;
import com.highvia.orderservice.entity.OrderItem;
import com.highvia.common.events.*;
import com.highvia.orderservice.repository.OrderRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


@Service
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate;
    @Value("${services.cart.url}")
    private String cartServiceUrl;

    @Value("${services.product.url}")
    private String productServiceUrl;

    public OrderService(OrderRepository orderRepository,
                        KafkaTemplate<String, Object> kafkaTemplate,
                        RestTemplate restTemplate) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public OrderResponse createOrder(Long userId, OrderRequest request) {
        log.info("[SAGA START] Creating order for user: {}", userId);

        // 1 Get cart from CartService
        List<CartItemDTO> cartItems = getCartItems(userId);

        if (cartItems == null || cartItems.isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        // 2. create Order
        OrderEntity order = new OrderEntity();
        order.setUserId(userId);
        order.setStatus("PENDING"); //First, set the order to pending
        order.setBuyerName(request.buyerName());
        order.setBuyerPhone(request.buyerPhone());
        order.setBuyerAddress(request.buyerAddress());

        // 3 Create each cartItem inside cart
        for (CartItemDTO cartItem : cartItems) {
            ProductDTO product = getProduct(cartItem.productId());

            if (product == null) {
                throw new RuntimeException("Product not found: " + cartItem.productId());
            }

            OrderItem item = new OrderItem();
            item.setProductId(product.productId());
            item.setProductName(product.productName());
            item.setProductPrice(product.price());
            item.setQuantity(cartItem.quantity());

            order.addItem(item);
        }

        // 4. Calculate price and save
        order.calculateTotal();
        OrderEntity savedOrder = orderRepository.save(order);

        log.info("Order created [Status: Pending]: {}", savedOrder.getOrderId());

        // 5. Send Kafka Event
        OrderCreatedEvent event = convertToEvent(savedOrder);

        /*log.info("=== DEBUG: Sending OrderCreatedEvent ===");
        log.info("Event class: {}", event.getClass().getName());
        log.info("Event content: {}", event);*/

        kafkaTemplate.send("order-events", event);
        StockDeductionRequest reservationEvent = new StockDeductionRequest(
                savedOrder.getOrderId(),
                //savedOrder.getUserId(),
                savedOrder.getItems().stream().map(item -> new StockDeductionRequest.StockItem(
                        item.getProductId(), item.getQuantity()
                )).collect(Collectors.toList())
        );

        kafkaTemplate.send("stock-deduction-requests", reservationEvent);
        log.info(" [SAGA EVENT] Stock deduction requested: orderId={}", savedOrder.getOrderId());

        // 6. Clear cart: Don't clear now, wait until confirmation

        return convertToResponse(savedOrder);
    }

    private List<CartItemDTO> getCartItems(Long userId) {
        try {
            // Get current request's JWT token
            ServletRequestAttributes attributes = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();

            if (attributes == null) {
                throw new RuntimeException("No request context available");
            }

            HttpServletRequest request = attributes.getRequest();
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new RuntimeException("No valid Authorization header found");
            }

            // 创建带 Authorization header 的请求
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            String url = cartServiceUrl + "/api/cart";

            // 使用 exchange 方法传递 headers
            ResponseEntity<CartItemDTO[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    CartItemDTO[].class
            );

            CartItemDTO[] items = response.getBody();
            return items != null ? Arrays.asList(items) : List.of();
        } catch (Exception e) {
            log.error("Failed to get cart: {}", e.getMessage());
            throw new RuntimeException("Failed to get cart");
        }
        /*try {
            String url = cartServiceUrl + "/api/cart";
            CartItemDTO[] items = restTemplate.getForObject(url, CartItemDTO[].class);
            return items != null ? Arrays.asList(items) : List.of();
        } catch (Exception e) {
            log.error("Failed to get cart: {}", e.getMessage());
            throw new RuntimeException("Failed to get cart");
        }*/
    }

    //cancel order when stock reduction fails
    @Transactional
    public void cancelOrder(String orderId, String reason) {
        log.warn("[SAGA] Cancelling order: {}, reason {}", orderId, reason);
        OrderEntity order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        if (!"PENDING".equals(order.getStatus())) {
            log.warn("Order {} is not PENDING, current status: {}",
                    orderId, order.getStatus());
            return;
        }
        order.setStatus("CANCELLED");
        orderRepository.save(order);

        // Send Order cancelled event
        OrderCancelledEvent event = new OrderCancelledEvent(
                orderId,
                LocalDateTime.now()
        );
        kafkaTemplate.send("order-events", event);

        log.info("Order cancelled event sent: orderId={}", orderId);
    }

    //continue when stock reduction succeeded
    @Transactional
    public void confirmOrder(String orderId) {
        log.info("[SAGA SUCCESS] Confirming Order: {}", orderId);

        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found:" + orderId));

        if (!"PENDING".equals(order.getStatus())) {
            log.warn("Order {} is not PENDING, current status: {}", orderId, order.getStatus());
            return;
        }

        order.setStatus("CONFIRMED");
        orderRepository.save(order);
        log.info("✅ Step 1: Database updated for {}", orderId);

        // Step 2: 构造event
        OrderConfirmedEvent event;
        try {
            event = new OrderConfirmedEvent(
                    orderId,
                    LocalDateTime.now()
            );
            log.info("✅ Step 2: Event constructed for {}", orderId);
        } catch (Exception e) {
            log.error("❌ Failed to construct event for {}: {}", orderId, e.getMessage(), e);
            throw e;
        }

        // Step 3: 发送event
        try {
            kafkaTemplate.send("order-events", event);
            log.info("✅ Step 3: Event sent for {}", orderId);
        } catch (Exception e) {
            log.error("❌ Failed to send event for {}: {}", orderId, e.getMessage(), e);
            throw e;
        }

        // Step 4: 清理cart
        try {
            clearCart(order.getUserId());
            log.info("✅ Step 4: Cart cleared for {}", orderId);
        } catch (Exception e) {
            log.error("❌ Failed to clear cart for {}: {}", orderId, e.getMessage(), e);
            // 不抛出，clearCart失败不应该影响订单确认
        }

        log.info("✅ confirmOrder完成: {}", orderId);
    }

    public ProductDTO getProduct(Long productId) {
        try {
            String url = productServiceUrl + "/api/products/" + productId;
            return restTemplate.getForObject(url, ProductDTO.class);
        } catch (Exception e) {
            log.error("Failed to get product {}: {}", productId, e.getMessage());
            return null;
        }
    }

    private void clearCart(Long userId) {
        try {
            //String url = cartServiceUrl + "/api/cart";
            String url = cartServiceUrl + "/api/cart/internal/" + userId;
            restTemplate.delete(url);
            log.info("Cart cleared for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to clear cart: {}", e.getMessage());
        }
    }

    public OrderCreatedEvent convertToEvent(OrderEntity order) {
        List<OrderItemDTO> items = order.getItems().stream()
                .map(item -> new OrderItemDTO(
                        item.getProductId(),
                        item.getProductName(),
                        item.getProductPrice(),
                        item.getQuantity(),
                        item.getSubtotal()
                ))
                .collect(Collectors.toList());

        return new OrderCreatedEvent(
                order.getOrderId(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getBuyerName(),
                order.getBuyerPhone(),
                order.getBuyerAddress(),
                items,
                order.getCreatedAt()
        );
    }

    private OrderResponse convertToResponse(OrderEntity order) {
        List<OrderItemDTO> items = order.getItems().stream()
                .map(item -> new OrderItemDTO(
                        item.getProductId(),
                        item.getProductName(),
                        item.getProductPrice(),
                        item.getQuantity(),
                        item.getSubtotal()
                ))
                .collect(Collectors.toList());

        return new OrderResponse(
                order.getOrderId(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getBuyerName(),
                order.getBuyerPhone(),
                order.getBuyerAddress(),
                items,
                order.getCreatedAt()
        );
    }
    
    //Kafka listeners
    /**
     * SAGA: Stock deduction success
     */
    @KafkaListener(topics = "stock-deduction-success", groupId = "order-service")
    public void handleStockSuccess(StockDeductionSuccess event) {
        log.info("[SAGA] Stock deduction succeeded: {}", event.orderId());
        confirmOrder(event.orderId());
    }

    /**
     * SAGA: Stock deduction failed
     */
    @KafkaListener(topics = "stock-deduction-failed", groupId = "order-service")
    public void handleStockFailed(StockDeductionFailed event) {
        log.error("[SAGA] Stock deduction failed: {}, reason: {}",
                event.orderId(), event.reason());
        cancelOrder(event.orderId(), event.reason());
    }
}
/*
    @CircuitBreaker(name = "customerService", fallbackMethod = "createOrderFallback")
    public OrderResponse createOrder(OrderRequest request) {
        System.out.println("\n=== Creating Order ===");

        System.out.println("Validating customer: " + request.customerEmail());
        CustomerDto customer = customerClient.getCustomer(request.customerEmail());
        successCount++;
        System.out.println("Customer validated | Total Success: " + successCount);

        System.out.println("🔍 Calling Product Service for productId: " + request.productId());
        ProductDto product = productClient.getProduct(request.productId());

        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }

        if (product.stock() < request.quantity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient stock. Available: " + product.stock());
        }

        Double totalPrice = product.price() * request.quantity();
        Long orderId = orderIdGenerator.getAndIncrement();
        String createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);

        String customerName = (customer != null && customer.username() != null)
                ? customer.username()
                : request.customerEmail();

        System.out.println(" Order created: #" + orderId +
                " | Customer: " + customerName +
                " | Product: " + product.name() +
                " | Quantity: " + request.quantity() +
                " | Total: $" + totalPrice);

        return new OrderResponse(
                orderId,
                product.productId(),
                product.name(),
                product.price(),
                request.quantity(),
                totalPrice,
                request.customerEmail(),
                "CREATED",
                createdAt
        );
    }

    /*private OrderResponse createOrderFallback(OrderRequest request, Exception e) {
        fallbackCount++;
        failureCount++;
        System.out.println(" CIRCUIT BREAKER ACTIVATED for " + request.customerEmail() +
                " | Failures: " + failureCount + " | Fallbacks: " + fallbackCount);
        System.out.println("    Error: " + e.getMessage());

        Long orderId = orderIdGenerator.getAndIncrement();
        String createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);

        ProductDto product = productClient.getProduct(request.productId());

        Double totalPrice = product.price() * request.quantity();

        System.out.println(" Order created with GUEST user: #" + orderId);

        return new OrderResponse(
                orderId,
                product.productId(),
                product.name(),
                product.price(),
                request.quantity(),
                totalPrice,
                request.customerEmail(),
                "CREATED_WITH_FALLBACK",  // ← 标记这是fallback创建的
                createdAt
        );
    }*/
/*
    public OrderResponse createOrderFallback(OrderRequest request, Exception e) {
        fallbackCount++;
        failureCount++;
        System.out.println(" CIRCUIT BREAKER ACTIVATED for " + request.customerEmail() +
                " | Failures: " + failureCount + " | Fallbacks: " + fallbackCount);
        System.out.println("    Error: " + e.getMessage());

        // Fallback uses mock data
        Long orderId = orderIdGenerator.getAndIncrement();
        String createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);

        // Mock product
        String productId = request.productId();
        String productName = "Sample Product";
        Double productPrice = 19.99;
        Double totalPrice = productPrice * request.quantity();

        System.out.println("✅ Order created with fallback (mock data): #" + orderId);

        return new OrderResponse(
                orderId,
                productId,
                productName,
                productPrice,
                request.quantity(),
                totalPrice,
                request.customerEmail(),
                "CREATED_WITH_FALLBACK",
                createdAt
        );
    }*/

    /*
    public String getStats() {
        int totalCalls = successCount + failureCount;
        double successRate = totalCalls > 0 ? (successCount * 100.0 / totalCalls) : 0;
        return String.format("Total Calls: %d | Success: %d (%.1f%%) | Failure: %d | Fallback Used: %d",
                totalCalls, successCount, successRate, failureCount, fallbackCount);
    }*/
