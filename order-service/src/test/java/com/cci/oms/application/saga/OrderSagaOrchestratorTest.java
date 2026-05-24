package com.cci.oms.application.saga;

import com.cci.oms.application.config.TestCacheConfig;
import com.cci.oms.application.dto.CreateOrderRequest;
import com.cci.oms.application.dto.OrderResponse;
import com.cci.oms.application.port.InventoryPort;
import com.cci.oms.application.service.OrderService;
import com.cci.oms.domain.model.Order;
import com.cci.oms.domain.model.OrderStatus;
import com.cci.oms.domain.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Import(TestCacheConfig.class)
class OrderSagaOrchestratorTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    // Replaces StubInventoryAdapter; void methods succeed by default (Mockito default)
    @MockitoBean
    private InventoryPort inventoryPort;

    @Test
    void createOrder_sagaConfirmsAndShipsOrder() {
        OrderResponse response = orderService.createOrder(buildRequest("CUST-SAGA-001"));

        Order order = orderRepository.findById(response.id()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void createOrder_whenInventoryFails_sagaCancelsOrder() {
        doThrow(new RuntimeException("Out of stock"))
                .when(inventoryPort).reserveStock(any(UUID.class), any(String.class), anyInt());

        OrderResponse response = orderService.createOrder(buildRequest("CUST-SAGA-002"));

        Order order = orderRepository.findById(response.id()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    private CreateOrderRequest buildRequest(String customerId) {
        CreateOrderRequest r = new CreateOrderRequest();
        r.setCustomerId(customerId);
        r.setProductId("PROD-SAGA-001");
        r.setQuantity(1);
        r.setTotalAmount(new BigDecimal("29.99"));
        return r;
    }
}