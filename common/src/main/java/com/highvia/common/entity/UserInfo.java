package com.highvia.common.entity;
import com.highvia.common.enums.UserRole;

public record UserInfo(
        Long id,
        String email,
        String username,
        UserRole role
) {
    public boolean isAdmin() {
        return UserRole.ADMIN.equals(role);
    }

    public boolean isUser() {
        return UserRole.USER.equals(role);
    }

    public boolean isSeller() {
        return UserRole.SELLER.equals(role);
    }

    public boolean isCustomerService() {
        return UserRole.CUSTOMER_SERVICE.equals(role);
    }
}
