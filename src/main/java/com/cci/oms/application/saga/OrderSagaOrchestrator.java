package com.cci.oms.application.saga;

import com.cci.oms.application.port.InventoryPort;
import com.cci.oms.application.service.OrderService;
import com.cci.oms.domain.event.OrderCreatedEvent;
import com.cci.oms.domain.event.OrderStatusChangedEvent;
import com.cci.oms.domain.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderSagaOrchestrator {

    private final OrderService orderService;
    private final InventoryPort inventoryPort;

    // Step 1: PENDING → CONFIRMED
    // Fires after the createOrder transaction commits so the order row is visible.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Saga step 1: confirming order {}", event.orderId());
        try {
            orderService.updateOrderStatus(event.orderId(), OrderStatus.CONFIRMED);
        } catch (RuntimeException e) {
            log.error("Saga step 1 failed for order {}, compensating", event.orderId(), e);
            compensate(event.orderId());
        }
    }

    // Step 2: CONFIRMED → SHIPPED (with inventory reservation)
    // Fires after the REQUIRES_NEW transaction from step 1 commits.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        if (event.newStatus() != OrderStatus.CONFIRMED) {
            return;
        }
        log.info("Saga step 2: shipping order {}", event.orderId());
        try {
            inventoryPort.reserveStock(event.orderId(), event.productId(), event.quantity());
            orderService.updateOrderStatus(event.orderId(), OrderStatus.SHIPPED);
        } catch (RuntimeException e) {
            log.error("Saga step 2 failed for order {}, compensating", event.orderId(), e);
            compensate(event.orderId());
        }
    }

    // Compensating transaction: cancel the order on any saga failure
    private void compensate(UUID orderId) {
        try {
            orderService.updateOrderStatus(orderId, OrderStatus.CANCELLED);
            log.info("Saga compensation: order {} cancelled", orderId);
        } catch (RuntimeException ex) {
            log.error("Saga compensation failed for order {}", orderId, ex);
        }
    }
}