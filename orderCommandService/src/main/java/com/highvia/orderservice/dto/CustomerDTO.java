package com.highvia.orderservice.dto;

public record CustomerDTO(
        Long id,
        String email,
        String username
) {
}
