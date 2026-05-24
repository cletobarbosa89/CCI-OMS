package com.cci.oms.application.service;

import com.cci.oms.application.dto.CreateOrderRequest;
import com.cci.oms.application.dto.OrderFilterRequest;
import com.cci.oms.application.dto.OrderResponse;
import com.cci.oms.application.exception.InvalidOrderStatusTransitionException;
import com.cci.oms.application.exception.OrderNotFoundException;
import com.cci.oms.application.mapper.OrderMapper;
import com.cci.oms.domain.model.Order;
import com.cci.oms.domain.model.OrderStatus;
import com.cci.oms.domain.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ApplicationEventPublisher publisher;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_validRequest_returnsResponseWithPendingStatus() {
        CreateOrderRequest request = buildCreateRequest();
        Order mappedOrder = Order.builder().build();
        Order savedOrder = Order.builder().id(UUID.randomUUID()).customerId("CUST-001")
                .productId("PROD-001").quantity(2).status(OrderStatus.PENDING).build();
        OrderResponse expected = buildOrderResponse(savedOrder.getId(), OrderStatus.PENDING);

        when(orderMapper.toEntity(request)).thenReturn(mappedOrder);
        when(orderRepository.save(mappedOrder)).thenReturn(savedOrder);
        when(orderMapper.toResponse(savedOrder)).thenReturn(expected);

        OrderResponse result = orderService.createOrder(request);

        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(mappedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository).save(mappedOrder);
    }

    @Test
    void getAllOrders_noFilter_returnsMappedPage() {
        UUID id = UUID.randomUUID();
        Order order = Order.builder().id(id).status(OrderStatus.PENDING).build();
        OrderResponse response = buildOrderResponse(id, OrderStatus.PENDING);
        Page<Order> orderPage = new PageImpl<>(List.of(order));

        when(orderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(orderPage);
        when(orderMapper.toResponse(order)).thenReturn(response);

        OrderFilterRequest filter = OrderFilterRequest.builder().build();
        Page<OrderResponse> result = orderService.getAllOrders(filter, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void getAllOrders_withStatusFilter_delegatesSpecificationToRepository() {
        UUID id = UUID.randomUUID();
        Order order = Order.builder().id(id).status(OrderStatus.CONFIRMED).build();
        OrderResponse response = buildOrderResponse(id, OrderStatus.CONFIRMED);
        Page<Order> orderPage = new PageImpl<>(List.of(order));

        when(orderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(orderPage);
        when(orderMapper.toResponse(order)).thenReturn(response);

        OrderFilterRequest filter = OrderFilterRequest.builder().status(OrderStatus.CONFIRMED).build();
        Page<OrderResponse> result = orderService.getAllOrders(filter, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void getOrderById_orderExists_returnsOrderResponse() {
        UUID id = UUID.randomUUID();
        Order order = Order.builder().id(id).status(OrderStatus.PENDING).build();
        OrderResponse expected = buildOrderResponse(id, OrderStatus.PENDING);

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(expected);

        OrderResponse result = orderService.getOrderById(id);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getOrderById_orderNotFound_throwsOrderNotFoundException() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(id))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void updateOrderStatus_validTransition_pendingToConfirmed() {
        UUID id = UUID.randomUUID();
        Order order = Order.builder().id(id).status(OrderStatus.PENDING).build();
        Order savedOrder = Order.builder().id(id).productId("PROD-001").quantity(2)
                .status(OrderStatus.CONFIRMED).build();
        OrderResponse expected = buildOrderResponse(id, OrderStatus.CONFIRMED);

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(savedOrder);
        when(orderMapper.toResponse(savedOrder)).thenReturn(expected);

        OrderResponse result = orderService.updateOrderStatus(id, OrderStatus.CONFIRMED);

        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void updateOrderStatus_invalidTransition_throwsException() {
        UUID id = UUID.randomUUID();
        Order order = Order.builder().id(id).status(OrderStatus.CANCELLED).build();
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(id, OrderStatus.CONFIRMED))
                .isInstanceOf(InvalidOrderStatusTransitionException.class)
                .hasMessageContaining("CANCELLED")
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    void deleteOrder_orderExists_setsDeletionTimestamp() {
        UUID id = UUID.randomUUID();
        Order order = Order.builder().id(id).status(OrderStatus.PENDING).build();

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.deleteOrder(id);

        assertThat(order.getDeletedAt()).isNotNull();
        assertThat(order.getDeletedAt()).isBeforeOrEqualTo(LocalDateTime.now());
        verify(orderRepository).save(order);
    }

    @Test
    void deleteOrder_orderNotFound_throwsOrderNotFoundException() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.deleteOrder(id))
                .isInstanceOf(OrderNotFoundException.class);
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