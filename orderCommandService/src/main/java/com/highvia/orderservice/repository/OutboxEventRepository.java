package com.highvia.orderservice.repository;

import com.highvia.orderservice.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findByProcessedFalseOrderByCreatedAtAsc(Pageable pageable);
}
