package com.cci.oms.infrastructure.adapter;

import com.cci.oms.application.port.InventoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Live adapter — calls the inventory-service over HTTP.
 * Activated by setting inventory.service.enabled=true in application.properties.
 * The inventory-service must be running at inventory.service.url (default: http://localhost:8081).
 *
 * Microservices pattern: OrderService depends on the InventoryPort interface (hexagonal),
 * not on this HTTP adapter directly. Swapping StubInventoryAdapter ↔ RestClientInventoryAdapter
 * is a config change, not a code change.
 */
@Component
@ConditionalOnProperty(name = "inventory.service.enabled", havingValue = "true")
@Slf4j
public class RestClientInventoryAdapter implements InventoryPort {

    private final RestClient restClient;

    public RestClientInventoryAdapter(
            @Value("${inventory.service.url:http://localhost:8081}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        log.info("RestClientInventoryAdapter initialised — targeting {}", baseUrl);
    }

    @Override
    public void reserveStock(UUID orderId, String productId, int quantity) {
        log.info("Calling inventory-service to reserve {} x {} for order {}", quantity, productId, orderId);
        restClient.post()
                .uri("/api/inventory/reserve")
                .body(new StockReservationRequest(orderId, productId, quantity))
                .retrieve()
                .toBodilessEntity();
    }

    record StockReservationRequest(UUID orderId, String productId, int quantity) {}
}