package com.highvia.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.highvia.orderservice.entity.OutboxEvent;
import com.highvia.orderservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findByProcessedFalseOrderByCreatedAtAsc(PageRequest.of(0, 100));
        for (OutboxEvent event : events) {
            try {
                Class<?> eventClass = Class.forName(event.getEventType());
                Object payload = objectMapper.readValue(event.getPayload(), eventClass);
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), payload).get(5, TimeUnit.SECONDS);
                event.setProcessed(true);
                event.setProcessedAt(LocalDateTime.now());
                outboxEventRepository.save(event);
                log.info("[OUTBOX] Published: topic={}, aggregateId={}, type={}",
                        event.getTopic(), event.getAggregateId(), event.getEventType());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[OUTBOX] Interrupted while publishing event id={}: {}", event.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("[OUTBOX] Failed to publish event id={}, topic={}, aggregateId={}: {}",
                        event.getId(), event.getTopic(), event.getAggregateId(), e.getMessage());
            }
        }
    }
}
