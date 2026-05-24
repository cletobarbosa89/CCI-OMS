package com.cci.inventory.controller;

import com.cci.inventory.dto.StockReservationRequest;
import com.cci.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/reserve")
    public ResponseEntity<Void> reserve(@Valid @RequestBody StockReservationRequest request) {
        log.info("Reserve request: {} x {} for order {}", request.quantity(), request.productId(), request.orderId());
        inventoryService.reserveStock(request.orderId(), request.productId(), request.quantity());
        return ResponseEntity.ok().build();
    }
}