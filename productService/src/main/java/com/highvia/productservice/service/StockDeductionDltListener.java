package com.highvia.productservice.service;

import com.highvia.common.events.StockDeductionRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class StockDeductionDltListener {

    @KafkaListener(
            topics = "stock-deduction-requests-dlt",
            groupId = "product-service-dlt",
            containerFactory = "dltListenerContainerFactory"
    )
    public void handleDlt(ConsumerRecord<String, StockDeductionRequest> record) {
        StockDeductionRequest request = record.value();
        if (request == null) {
            // Deserialization failed — the most common reason a message lands in the DLT
            log.error("[DLT] stock-deduction-requests dead letter: orderId={} (deserialization failed), partition={}, offset={}",
                    record.key(), record.partition(), record.offset());
            return;
        }
        log.error("[DLT] stock-deduction-requests dead letter: orderId={}, partition={}, offset={}, items={}",
                request.orderId(),
                record.partition(),
                record.offset(),
                request.items());
    }
}
