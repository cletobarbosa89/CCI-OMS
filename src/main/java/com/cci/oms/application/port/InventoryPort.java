package com.cci.oms.application.port;

import java.util.UUID;

public interface InventoryPort {
    void reserveStock(UUID orderId, String productId, int quantity);
}