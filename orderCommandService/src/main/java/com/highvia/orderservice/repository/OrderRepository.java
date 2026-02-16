package com.highvia.orderservice.repository;

import com.highvia.orderservice.entity.OrderEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {
    List<OrderEntity> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId ORDER BY o.createdAt DESC")
    List<OrderEntity> findByUserIdWithItems(@Param("userId") Long userId);

    List<OrderEntity> findByStatus(String status);
    Optional<OrderEntity> findByOrderIdAndUserId(String orderId, Long userId);
    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
}
