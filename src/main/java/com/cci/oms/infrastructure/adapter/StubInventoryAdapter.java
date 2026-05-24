package com.cci.oms.infrastructure.adapter;

import com.cci.oms.application.port.InventoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class StubInventoryAdapter implements InventoryPort {

    @Override
    public void reserveStock(UUID orderId, String productId, int quantity) {
        log.info("Stub inventory: reserving {} unit(s) of {} for order {}", quantity, productId, orderId);
    }
}