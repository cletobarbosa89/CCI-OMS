package com.cci.inventory.dto;

import java.util.UUID;

public record StockReservationRequest(UUID orderId, String productId, int quantity) {}