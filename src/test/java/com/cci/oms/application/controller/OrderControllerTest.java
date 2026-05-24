package com.cci.oms.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cci.oms.application.dto.CreateOrderRequest;
import com.cci.oms.application.dto.OrderFilterRequest;
import com.cci.oms.application.dto.OrderResponse;
import com.cci.oms.application.dto.UpdateOrderStatusRequest;
import com.cci.oms.application.exception.OrderNotFoundException;
import com.cci.oms.application.service.OrderService;
import com.cci.oms.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createOrder_validRequest_returns201WithBody() throws Exception {
        CreateOrderRequest request = buildCreateRequest();
        OrderResponse response = buildOrderResponse(UUID.randomUUID(), OrderStatus.PENDING);

        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerId").value("CUST-001"))
                .andExpect(jsonPath("$.productId").value("PROD-001"));
    }

    @Test
    void createOrder_blankCustomerId_returns400WithDetails() throws Exception {
        CreateOrderRequest request = buildCreateRequest();
        request.setCustomerId("");

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void createOrder_negativeQuantity_returns400() throws Exception {
        CreateOrderRequest request = buildCreateRequest();
        request.setQuantity(-1);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllOrders_noFilter_returns200WithPageContent() throws Exception {
        OrderResponse response = buildOrderResponse(UUID.randomUUID(), OrderStatus.PENDING);
        when(orderService.getAllOrders(any(OrderFilterRequest.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].customerId").value("CUST-001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAllOrders_withStatusFilter_passesFilterToService() throws Exception {
        OrderResponse response = buildOrderResponse(UUID.randomUUID(), OrderStatus.CONFIRMED);
        when(orderService.getAllOrders(any(OrderFilterRequest.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/orders").param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));
    }

    @Test
    void getAllOrders_withCustomerIdFilter_returns200() throws Exception {
        OrderResponse response = buildOrderResponse(UUID.randomUUID(), OrderStatus.PENDING);
        when(orderService.getAllOrders(any(OrderFilterRequest.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/orders").param("customerId", "CUST-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerId").value("CUST-001"));
    }

    @Test
    void getOrderById_orderExists_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        OrderResponse response = buildOrderResponse(id, OrderStatus.PENDING);
        when(orderService.getOrderById(id)).thenReturn(response);

        mockMvc.perform(get("/api/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getOrderById_orderNotFound_returns404WithCorrelationId() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.getOrderById(id)).thenThrow(new OrderNotFoundException(id));

        mockMvc.perform(get("/api/orders/{id}", id)
                        .header("X-Correlation-ID", "test-cid-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.correlationId").value("test-cid-999"));
    }

    @Test
    void createOrder_blankCustomerId_returns400WithCorrelationId() throws Exception {
        CreateOrderRequest request = buildCreateRequest();
        request.setCustomerId("");

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Correlation-ID", "test-cid-400"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.correlationId").value("test-cid-400"));
    }

    @Test
    void updateOrderStatus_validTransition_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.CONFIRMED);
        OrderResponse response = buildOrderResponse(id, OrderStatus.CONFIRMED);

        when(orderService.updateOrderStatus(eq(id), eq(OrderStatus.CONFIRMED))).thenReturn(response);

        mockMvc.perform(patch("/api/orders/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void deleteOrder_orderExists_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(orderService).deleteOrder(id);

        mockMvc.perform(delete("/api/orders/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteOrder_orderNotFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new OrderNotFoundException(id)).when(orderService).deleteOrder(id);

        mockMvc.perform(delete("/api/orders/{id}", id))
                .andExpect(status().isNotFound());
    }

    private CreateOrderRequest buildCreateRequest() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId("CUST-001");
        request.setProductId("PROD-001");
        request.setQuantity(2);
        request.setTotalAmount(new BigDecimal("19.99"));
        return request;
    }

    private OrderResponse buildOrderResponse(UUID id, OrderStatus status) {
        return new OrderResponse(id, "CUST-001", "PROD-001", 2,
                new BigDecimal("19.99"), status, LocalDateTime.now(), LocalDateTime.now());
    }
}