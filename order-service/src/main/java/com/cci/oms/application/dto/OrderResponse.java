package com.cci.oms.application.dto;

import com.cci.oms.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// Java record: immutable, auto-generates constructor, getters, equals, hashCode, toString
public record OrderResponse(
        UUID id,
        String customerId,
        String productId,
        Integer quantity,
        BigDecimal totalAmount,
        OrderStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}