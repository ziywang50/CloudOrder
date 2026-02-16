package com.highvia.customerservice.Dto;

public record RegisterDto(
        String email,
        String password,
        String username
) {
    public RegisterDto(String email, String password, String username) {
        this.email = email;
        this.password = password;
        this.username = username;
    }
}
