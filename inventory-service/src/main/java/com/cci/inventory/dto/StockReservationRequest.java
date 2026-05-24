package com.cci.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StockReservationRequest(
        @NotNull UUID orderId,
        @NotBlank String productId,
        @Min(1) int quantity
) {}