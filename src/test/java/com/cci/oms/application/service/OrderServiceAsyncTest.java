package com.cci.oms.application.service;

import com.cci.oms.application.config.TestCacheConfig;
import com.cci.oms.application.dto.OrderResponse;
import com.cci.oms.domain.model.Order;
import com.cci.oms.domain.model.OrderStatus;
import com.cci.oms.domain.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestCacheConfig.class)
class OrderServiceAsyncTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void getOrderByIdAsync_returnsCorrectOrder() throws Exception {
        Order saved = orderRepository.save(buildOrder("CUST-ASYNC-001"));

        CompletableFuture<OrderResponse> future = orderService.getOrderByIdAsync(saved.getId());
        OrderResponse response = future.get(5, TimeUnit.SECONDS);

        assertThat(response.id()).isEqualTo(saved.getId());
        assertThat(response.customerId()).isEqualTo("CUST-ASYNC-001");
    }

    @Test
    void getOrderByIdAsync_allOf_fetchesTwoOrdersInParallel() throws Exception {
        Order o1 = orderRepository.save(buildOrder("CUST-ASYNC-002"));
        Order o2 = orderRepository.save(buildOrder("CUST-ASYNC-003"));

        CompletableFuture<OrderResponse> f1 = orderService.getOrderByIdAsync(o1.getId());
        CompletableFuture<OrderResponse> f2 = orderService.getOrderByIdAsync(o2.getId());

        // allOf waits for both futures to complete, then we read results
        CompletableFuture.allOf(f1, f2).get(5, TimeUnit.SECONDS);

        assertThat(f1.get().id()).isEqualTo(o1.getId());
        assertThat(f2.get().id()).isEqualTo(o2.getId());
    }

    private Order buildOrder(String customerId) {
        return Order.builder()
                .customerId(customerId)
                .productId("PROD-001")
                .quantity(1)
                .totalAmount(new BigDecimal("9.99"))
                .status(OrderStatus.PENDING)
                .build();
    }
}