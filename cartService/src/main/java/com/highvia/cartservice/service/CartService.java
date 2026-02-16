package com.highvia.cartservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highvia.cartservice.dto.CartItem;
import com.highvia.cartservice.dto.UpdateQuantityRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;


import java.util.*;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String CART_KEY_PREFIX = "cart:";

    // Add to cart
    public void addToCart(Long userId, Long productId, Integer quantity) {
        String key = CART_KEY_PREFIX + userId;

        Long result = redisTemplate.opsForHash().increment(key, productId.toString(), quantity);

        // 立即验证
        Object check = redisTemplate.opsForHash().get(key, productId.toString());

        log.info("Added product {} to cart for user {}", productId, userId);
    }

    // Getcart
    public List<CartItem> getCart(Long userId) {
        String key = CART_KEY_PREFIX + userId;

        // Redis Hash HGETALL cart:1
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        return entries.entrySet().stream()
                .map(entry -> new CartItem(
                        Long.parseLong(entry.getKey().toString()),
                        Integer.parseInt(entry.getValue().toString())
                ))
                .collect(Collectors.toList());
    }

    // Update quantity
    public void updateQuantity(Long userId, Long productId, UpdateQuantityRequest request) {
        String key = CART_KEY_PREFIX + userId;
        Integer newQuantity = request.quantity();

        if (newQuantity <= 0) {
            removeItem(userId, productId);
            log.info("Removed product {} from cart (quantity <= 0)", productId);
            return;
        }

        // Redis Hash HSET cart:1 123 5
        redisTemplate.opsForHash().put(key, productId.toString(), newQuantity.toString());
    }

    // Delete item
    public void removeItem(Long userId, Long productId) {
        String key = CART_KEY_PREFIX + userId;

        // Redis Hash operation HDEL cart:1 123
        redisTemplate.opsForHash().delete(key, productId.toString());
    }

    // Remove cart completely
    public void clearCart(Long userId) {
        String key = CART_KEY_PREFIX + userId;

        // Redis DEL cart:1
        redisTemplate.delete(key);
        log.info("Cart cleared for user {}", userId);
    }

}
