package com.highvia.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highvia.orderservice.entity.OutboxEvent;
import com.highvia.orderservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Persists an outbox event in the same transaction as the caller.
     * The event will be published to Kafka by OutboxPublisher on the next poll.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(String topic, String aggregateId, Object event) {
        try {
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setTopic(topic);
            outboxEvent.setAggregateId(aggregateId);
            outboxEvent.setEventType(event.getClass().getName());
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
            outboxEventRepository.save(outboxEvent);
            log.debug("[OUTBOX] Queued: topic={}, aggregateId={}, type={}",
                    topic, aggregateId, event.getClass().getSimpleName());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event: " + event.getClass().getSimpleName(), e);
        }
    }
}
