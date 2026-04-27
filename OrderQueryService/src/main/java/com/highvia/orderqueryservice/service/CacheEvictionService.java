package com.highvia.orderqueryservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CacheEvictionService {

    @Caching(evict = {
            @CacheEvict(value = "userOrders", key = "#userId"),
            @CacheEvict(value = "userOrdersByStatus", key = "#userId"),
            @CacheEvict(value = "userOrderStats", key = "#userId")
    })
    public void evictUserCache(Long userId) {
        log.debug("Evicted cache for user: {}", userId);
    }

    @CacheEvict(value = "orderById", key = "#orderId")
    public void evictOrderCache(String orderId) {
        log.debug("Evicted cache for order: {}", orderId);
    }
}
