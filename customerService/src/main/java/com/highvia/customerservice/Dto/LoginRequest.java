package com.highvia.customerservice.Dto;

public record LoginRequest(
        String email,
        String password
) {
}
