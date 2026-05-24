package com.cci.inventory.controller;

import com.cci.inventory.dto.StockReservationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@Slf4j
public class InventoryController {

    @PostMapping("/reserve")
    public ResponseEntity<Void> reserve(@RequestBody StockReservationRequest request) {
        log.info("Reserving {} x {} for order {}", request.quantity(), request.productId(), request.orderId());
        // Stub: always succeeds. A real implementation would check and decrement stock in a database.
        return ResponseEntity.ok().build();
    }
}