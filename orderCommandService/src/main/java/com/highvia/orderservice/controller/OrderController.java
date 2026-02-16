package com.highvia.orderservice.controller;

import com.highvia.orderservice.dto.OrderRequest;
import com.highvia.orderservice.dto.OrderResponse;
import com.highvia.orderservice.entity.OrderEntity;
import com.highvia.orderservice.repository.OrderRepository;
import com.highvia.orderservice.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.highvia.common.annotation.CurrentUser;  // ← 加这个
import com.highvia.common.entity.UserInfo;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;

    public OrderController(OrderService orderService, OrderRepository orderRepository) {

        this.orderService = orderService;
        this.orderRepository = orderRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@CurrentUser UserInfo user, @RequestBody OrderRequest request) {
        return orderService.createOrder(user.id(), request);
    }

    @GetMapping("/writedb/user/{userId}")
    public ResponseEntity<List<OrderEntity>> getOrdersByUserId(@PathVariable Long userId) {
        List<OrderEntity> orders = orderRepository.findByUserIdWithItems(userId);
        return ResponseEntity.ok(orders);
    }

    /*
    @GetMapping("/stats")
    public String getStats() {
        return orderService.getStats();
    }*/
}