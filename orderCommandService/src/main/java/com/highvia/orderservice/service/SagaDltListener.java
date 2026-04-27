package com.highvia.orderservice.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SagaDltListener {
    @KafkaListener(topics = "stock-deduction-success-dlt", groupId = "saga-dlt-monitor")
    public void handleSuccessDlt(ConsumerRecord<String, Object> record) {
        log.error("[DLT] stock-deduction-success dead letter: key={}, value={}", record.key(), record.value());
    }

    @KafkaListener(topics = "stock-deduction-failed-dlt", groupId = "saga-dlt-monitor")
    public void handleFailedDlt(ConsumerRecord<String, Object> record) {
        log.error("[DLT] stock-deduction-failed dead letter: key={}, value={}",
                record.key(), record.value());
    }
}
