package com.cci.oms.application.saga;

import com.cci.oms.application.config.TestCacheConfig;
import com.cci.oms.application.dto.CreateOrderRequest;
import com.cci.oms.application.dto.OrderResponse;
import com.cci.oms.domain.model.OrderStatus;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies the full saga chain using the real RestClientInventoryAdapter + WireMock HTTP stub.
 *
 * Unlike OrderSagaOrchestratorTest (which mocks InventoryPort at the Java interface level),
 * this test exercises the actual HTTP call made by RestClientInventoryAdapter, catching
 * wiring bugs like wrong endpoint URLs, missing fields, or error-status handling.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
@Import(TestCacheConfig.class)
class OrderSagaRestClientIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void inventoryServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("inventory.service.enabled", () -> "true");
        registry.add("inventory.service.url", wireMock::baseUrl);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Test
    void createOrder_whenInventoryReservesStock_orderReachesShipped() {
        wireMock.stubFor(post(urlEqualTo("/api/inventory/reserve"))
                .willReturn(aResponse().withStatus(200)));

        ResponseEntity<OrderResponse> response = restTemplate.postForEntity(
                "/api/orders", buildRequest("P99"), OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID orderId = response.getBody().id();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(fetchStatus(orderId)).isEqualTo(OrderStatus.SHIPPED));

        wireMock.verify(postRequestedFor(urlEqualTo("/api/inventory/reserve"))
                .withRequestBody(matchingJsonPath("$.orderId", equalTo(orderId.toString())))
                .withRequestBody(matchingJsonPath("$.productId", equalTo("P99")))
                .withRequestBody(matchingJsonPath("$.quantity", equalTo("2"))));
    }

    @Test
    void createOrder_whenInventoryReturns500_sagaCancelsOrder() {
        wireMock.stubFor(post(urlEqualTo("/api/inventory/reserve"))
                .willReturn(aResponse().withStatus(500)));

        UUID orderId = createOrder("P99");

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(fetchStatus(orderId)).isEqualTo(OrderStatus.CANCELLED));
    }

    @Test
    void createOrder_whenInventoryReturns404_sagaCancelsOrder() {
        wireMock.stubFor(post(urlEqualTo("/api/inventory/reserve"))
                .willReturn(aResponse().withStatus(404)));

        UUID orderId = createOrder("UNKNOWN-PROD");

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(fetchStatus(orderId)).isEqualTo(OrderStatus.CANCELLED));
    }

    private UUID createOrder(String productId) {
        ResponseEntity<OrderResponse> response = restTemplate.postForEntity(
                "/api/orders", buildRequest(productId), OrderResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private OrderStatus fetchStatus(UUID orderId) {
        return restTemplate.getForObject("/api/orders/" + orderId, OrderResponse.class).status();
    }

    private CreateOrderRequest buildRequest(String productId) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId("CUST-SAGA-IT");
        request.setProductId(productId);
        request.setQuantity(2);
        request.setTotalAmount(new BigDecimal("39.98"));
        return request;
    }
}