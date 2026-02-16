package com.highvia.productservice.service;

import com.highvia.common.events.StockDeductionFailed;
import com.highvia.common.events.StockDeductionRequest;
import com.highvia.common.events.StockDeductionSuccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockDeductionListener {
    private final ProductService productService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "stock-deduction-requests", groupId = "product-service",containerFactory = "stockDeductionListenerFactory")

    public void handleStockDeduction(StockDeductionRequest request) {
        log.info("[SAGA EVENT] Stock deduction request: orderId={}", request.orderId());
        boolean allSuccess = true;
        List<StockDeductionRequest.StockItem> deductedItems = new ArrayList<> ();
        String failReason = "";

        try {
            for (StockDeductionRequest.StockItem item: request.items()) {
                boolean success = productService.deductStock(
                        item.productId(),
                        item.quantity()
                );
                if (!success) {
                    allSuccess = false;
                    failReason = "Insufficient stock for product: " + item.productId();
                    log.warn("! {}", failReason);
                    break;
                }
                deductedItems.add(item);
                log.info(" Stock deducted: product={}, qty={}",
                        item.productId(), item.quantity());
            }
            if (allSuccess) {
                StockDeductionSuccess success = new StockDeductionSuccess(
                        request.orderId()
                );
                kafkaTemplate.send("stock-deduction-success", success);
                log.info("[SAGA SUCCESS] Stock deduction succeeded: {}", request.orderId());
            } else {
                rollbackStock(deductedItems);

                StockDeductionFailed failed = new StockDeductionFailed(
                        request.orderId(),
                        failReason
                );
                kafkaTemplate.send("stock-deduction-failed", failed);
                log.error(" [SAGA FAILED] Stock deduction failed: {}", request.orderId());
            }
        } catch (Exception e) {
            rollbackStock(deductedItems);

            StockDeductionFailed failed = new StockDeductionFailed(
                    request.orderId(),
                    e.getMessage()
            );
            kafkaTemplate.send("stock-deduction-failed", failed);
            log.error(" [SAGA ERROR] Exception: {}", e.getMessage());
        }
    }

    //rollback
    private void rollbackStock(List<StockDeductionRequest.StockItem> deductedItems) {
        log.warn("[SAGA ROLLBACK] Rolling back {} items", deductedItems.size());
        for (StockDeductionRequest.StockItem item : deductedItems) {
            try {
                productService.addStock(item.productId(), item.quantity());
                log.info("Stock restored: product={}, +{}",
                item.productId(), item.quantity());
            } catch (Exception e) {
                log.error(" Rollback failed for product {}: {}",
                        item.productId(), e.getMessage());
            }
        }
    }
}
