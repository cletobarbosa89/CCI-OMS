package com.cci.oms.application.service;

import com.cci.oms.application.config.TestCacheConfig;
import com.cci.oms.application.dto.CreateOrderRequest;
import com.cci.oms.application.dto.OrderResponse;
import com.cci.oms.domain.event.OrderCreatedEvent;
import com.cci.oms.domain.event.OrderStatusChangedEvent;
import com.cci.oms.domain.model.Order;
import com.cci.oms.domain.model.OrderStatus;
import com.cci.oms.domain.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestCacheConfig.class)
@RecordApplicationEvents
class OrderServiceEventTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Test
    void createOrder_publishesOrderCreatedEvent() {
        OrderResponse response = orderService.createOrder(buildRequest("CUST-EVT-001"));

        assertThat(applicationEvents.stream(OrderCreatedEvent.class))
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    assertThat(e.orderId()).isEqualTo(response.id());
                    assertThat(e.customerId()).isEqualTo("CUST-EVT-001");
                    assertThat(e.productId()).isEqualTo("PROD-EVT-001");
                    assertThat(e.quantity()).isEqualTo(2);
                });
    }

    @Test
    void updateOrderStatus_publishesOrderStatusChangedEvent() {
        Order saved = orderRepository.save(Order.builder()
                .customerId("CUST-EVT-002")
                .productId("PROD-EVT-002")
                .quantity(1)
                .totalAmount(new BigDecimal("15.00"))
                .status(OrderStatus.PENDING)
                .build());

        // Use CANCELLED (terminal state) so the saga does not fire further events
        orderService.updateOrderStatus(saved.getId(), OrderStatus.CANCELLED);

        assertThat(applicationEvents.stream(OrderStatusChangedEvent.class))
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    assertThat(e.orderId()).isEqualTo(saved.getId());
                    assertThat(e.previousStatus()).isEqualTo(OrderStatus.PENDING);
                    assertThat(e.newStatus()).isEqualTo(OrderStatus.CANCELLED);
                });
    }

    private CreateOrderRequest buildRequest(String customerId) {
        CreateOrderRequest r = new CreateOrderRequest();
        r.setCustomerId(customerId);
        r.setProductId("PROD-EVT-001");
        r.setQuantity(2);
        r.setTotalAmount(new BigDecimal("19.99"));
        return r;
    }
}