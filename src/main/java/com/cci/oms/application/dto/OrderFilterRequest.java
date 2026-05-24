package com.cci.oms.application.dto;

import com.cci.oms.domain.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderFilterRequest {

    private OrderStatus status;
    private String customerId;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
}