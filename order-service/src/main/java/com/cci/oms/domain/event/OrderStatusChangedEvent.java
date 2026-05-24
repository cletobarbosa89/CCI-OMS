package com.cci.oms.domain.event;

import com.cci.oms.domain.model.OrderStatus;

import java.util.UUID;

public record OrderStatusChangedEvent(
        UUID orderId,
        OrderStatus previousStatus,
        OrderStatus newStatus,
        String productId,
        int quantity
) {}