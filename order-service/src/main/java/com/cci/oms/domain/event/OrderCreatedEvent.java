package com.cci.oms.domain.event;

import java.util.UUID;

public record OrderCreatedEvent(UUID orderId, String customerId, String productId, int quantity) {}