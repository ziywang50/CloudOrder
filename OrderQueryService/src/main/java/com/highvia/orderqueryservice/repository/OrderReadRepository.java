package com.highvia.orderqueryservice.repository;

import com.highvia.orderqueryservice.entity.OrderReadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderReadRepository extends JpaRepository<OrderReadEntity, String> {
    
    // Check by user ID using time descending order
    List<OrderReadEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    // Check by user Id and status
    List<OrderReadEntity> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    
    // Check by Status
    List<OrderReadEntity> findByStatusOrderByCreatedAtDesc(String status);
    
    // Check by time range
    List<OrderReadEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);
    
    // Check by userId and orderId
    Optional<OrderReadEntity> findByOrderIdAndUserId(String orderId, Long userId);
    
    // Use sql to improve complex queries
    @Query(value = "SELECT * FROM orders_read WHERE user_id = :userId AND year_month = :yearMonth ORDER BY created_at DESC", 
           nativeQuery = true)
    List<OrderReadEntity> findByUserIdAndYearMonth(@Param("userId") Long userId, @Param("yearMonth") Integer yearMonth);
    
    // Count by userId
    long countByUserId(Long userId);
    
    // Count by userID, status
    long countByUserIdAndStatus(Long userId, String status);
}