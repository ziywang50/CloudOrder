package com.highvia.cartservice.controller;

import com.highvia.cartservice.dto.AddToCartRequest;
import com.highvia.cartservice.dto.CartItem;
import com.highvia.cartservice.dto.UpdateQuantityRequest;
import com.highvia.cartservice.service.CartService;
import lombok.RequiredArgsConstructor;
import com.highvia.common.annotation.CurrentUser;
import com.highvia.common.entity.UserInfo;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;

    @PostMapping
    public void addToCart(@CurrentUser UserInfo user, @RequestBody AddToCartRequest request) {
        cartService.addToCart(user.id(), request.productId(), request.quantity());
    }

    @GetMapping
    public List<CartItem> getCart(@CurrentUser UserInfo user) {
        return cartService.getCart(user.id());
    }

    @DeleteMapping("/{productId}")
    public void removeItem(@CurrentUser UserInfo user,
                           @PathVariable Long productId) {
        cartService.removeItem(user.id(), productId);
    }

    @DeleteMapping
    public void clearCart(@CurrentUser UserInfo user) {
        cartService.clearCart(user.id());
    }

    @DeleteMapping("/internal/{userId}")
    public void clearCartInternal(@PathVariable Long userId) {
        cartService.clearCart(userId);
    }

    @PutMapping("/{productId}")
    public void updateQuantity(@CurrentUser UserInfo user,
                               @PathVariable Long productId,
                               @RequestBody UpdateQuantityRequest request) {
        cartService.updateQuantity(user.id(), productId, request);
    }
}
