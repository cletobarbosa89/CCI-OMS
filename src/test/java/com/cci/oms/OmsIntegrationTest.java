package com.cci.oms;

import com.cci.oms.application.dto.CreateOrderRequest;
import com.cci.oms.application.dto.OrderResponse;
import com.cci.oms.application.dto.UpdateOrderStatusRequest;
import com.cci.oms.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class OmsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createOrder_thenGetById_fullRoundTrip() {
        ResponseEntity<OrderResponse> createResponse = restTemplate.postForEntity(
                "/api/orders", buildCreateRequest(), OrderResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();

        UUID orderId = createResponse.getBody().id();

        ResponseEntity<OrderResponse> getResponse = restTemplate.getForEntity(
                "/api/orders/{id}", OrderResponse.class, orderId);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Saga runs synchronously: PENDING → CONFIRMED → SHIPPED
        assertThat(getResponse.getBody().status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(getResponse.getBody().customerId()).isEqualTo("CUST-001");
    }

    @Test
    void updateOrderStatus_invalidTransitionFromShipped_returns409() {
        // Saga moves order to SHIPPED immediately; any further status change is invalid
        ResponseEntity<OrderResponse> createResponse = restTemplate.postForEntity(
                "/api/orders", buildCreateRequest(), OrderResponse.class);
        UUID orderId = createResponse.getBody().id();

        UpdateOrderStatusRequest updateRequest = new UpdateOrderStatusRequest();
        updateRequest.setStatus(OrderStatus.CONFIRMED);

        ResponseEntity<String> patchResponse = restTemplate.exchange(
                "/api/orders/{id}/status",
                HttpMethod.PATCH,
                new HttpEntity<>(updateRequest),
                String.class,
                orderId);

        assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void deleteOrder_thenGetById_returns404() {
        ResponseEntity<OrderResponse> createResponse = restTemplate.postForEntity(
                "/api/orders", buildCreateRequest(), OrderResponse.class);
        UUID orderId = createResponse.getBody().id();

        restTemplate.delete("/api/orders/{id}", orderId);

        ResponseEntity<String> getResponse = restTemplate.getForEntity(
                "/api/orders/{id}", String.class, orderId);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getAllOrders_afterCreatingTwo_returnsBothInPage() {
        restTemplate.postForEntity("/api/orders", buildCreateRequest(), OrderResponse.class);
        restTemplate.postForEntity("/api/orders", buildCreateRequest(), OrderResponse.class);

        ResponseEntity<String> response = restTemplate.getForEntity("/api/orders", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("content");
    }

    private CreateOrderRequest buildCreateRequest() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId("CUST-001");
        request.setProductId("PROD-001");
        request.setQuantity(2);
        request.setTotalAmount(new BigDecimal("19.99"));
        return request;
    }
}