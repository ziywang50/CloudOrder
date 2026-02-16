package com.highvia.customerservice.Event;

public class UserCreatedEvent {
    private final Long userId;
    private final String email;

    public UserCreatedEvent(Long userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }
}