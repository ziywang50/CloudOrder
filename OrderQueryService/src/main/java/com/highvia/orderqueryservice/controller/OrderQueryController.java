package com.highvia.orderqueryservice.controller;

import com.highvia.orderqueryservice.dto.OrderReadDTO;
import com.highvia.orderqueryservice.service.OrderQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderQueryController {
    
    private final OrderQueryService orderQueryService;
    
    /**
     * Get Order by UserId
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderReadDTO>> getOrdersByUserId(@PathVariable Long userId) {
        log.info("REST request to get orders for user: {}", userId);
        List<OrderReadDTO> orders = orderQueryService.getOrdersByUserId(userId);
        return ResponseEntity.ok(orders);
    }
    
    /**
     * 获取用户特定状态的订单
     */
    @GetMapping("/user/{userId}/status/{status}")
    public ResponseEntity<List<OrderReadDTO>> getOrdersByUserIdAndStatus(
            @PathVariable Long userId, 
            @PathVariable String status) {
        log.info("REST request to get orders for user: {} with status: {}", userId, status);
        List<OrderReadDTO> orders = orderQueryService.getOrdersByUserIdAndStatus(userId, status);
        return ResponseEntity.ok(orders);
    }
    
    /**
     * 获取单个订单详情
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderReadDTO> getOrderById(@PathVariable String orderId) {
        log.info("REST request to get order: {}", orderId);
        return orderQueryService.getOrderById(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 获取用户订单统计
     */
    @GetMapping("/user/{userId}/stats")
    public ResponseEntity<OrderQueryService.OrderStatsDTO> getUserOrderStats(@PathVariable Long userId) {
        log.info("REST request to get order stats for user: {}", userId);
        OrderQueryService.OrderStatsDTO stats = orderQueryService.getUserOrderStats(userId);
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OrderQueryService is running");
    }
}