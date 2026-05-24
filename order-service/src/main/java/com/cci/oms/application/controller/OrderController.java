package com.cci.oms.application.controller;

import com.cci.oms.application.dto.CreateOrderRequest;
import com.cci.oms.application.dto.OrderFilterRequest;
import com.cci.oms.application.dto.OrderResponse;
import com.cci.oms.application.dto.UpdateOrderStatusRequest;
import com.cci.oms.application.service.OrderService;
import com.cci.oms.domain.model.OrderStatus;
import com.cci.oms.generated.api.OrdersApi;
import com.cci.oms.generated.model.OrderPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class OrderController implements OrdersApi {

    private final OrderService orderService;

    @Override
    public ResponseEntity<OrderResponse> createOrder(CreateOrderRequest createOrderRequest) {
        log.info("Creating order for customer {}", createOrderRequest.getCustomerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(createOrderRequest));
    }

    @Override
    public ResponseEntity<OrderPage> getAllOrders(Integer page, Integer size, String sort,
                                                   OrderStatus status, String customerId,
                                                   OffsetDateTime dateFrom, OffsetDateTime dateTo) {
        OrderFilterRequest filter = OrderFilterRequest.builder()
                .status(status)
                .customerId(customerId)
                .dateFrom(dateFrom != null ? dateFrom.toLocalDateTime() : null)
                .dateTo(dateTo != null ? dateTo.toLocalDateTime() : null)
                .build();
        return ResponseEntity.ok(toOrderPage(orderService.getAllOrders(filter, buildPageable(page, size, sort))));
    }

    @Override
    public ResponseEntity<OrderResponse> getOrderById(UUID id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @Override
    public ResponseEntity<OrderResponse> updateOrderStatus(UUID id, UpdateOrderStatusRequest updateOrderStatusRequest) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, updateOrderStatusRequest.getStatus()));
    }

    @Override
    public ResponseEntity<Void> deleteOrder(UUID id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    private Pageable buildPageable(Integer page, Integer size, String sort) {
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            return PageRequest.of(pageNum, pageSize, Sort.by(direction, parts[0].trim()));
        }
        return PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private OrderPage toOrderPage(Page<OrderResponse> springPage) {
        return new OrderPage()
                .content(springPage.getContent())
                .totalElements(springPage.getTotalElements())
                .totalPages(springPage.getTotalPages())
                .number(springPage.getNumber())
                .size(springPage.getSize())
                .first(springPage.isFirst())
                .last(springPage.isLast());
    }
}