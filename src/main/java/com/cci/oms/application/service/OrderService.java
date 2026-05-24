package com.cci.oms.application.service;

import com.cci.oms.application.config.CacheConfig;
import com.cci.oms.application.dto.CreateOrderRequest;
import com.cci.oms.application.dto.OrderFilterRequest;
import com.cci.oms.application.dto.OrderResponse;
import com.cci.oms.application.exception.InvalidOrderStatusTransitionException;
import com.cci.oms.application.exception.OrderNotFoundException;
import com.cci.oms.application.mapper.OrderMapper;
import com.cci.oms.domain.event.OrderCreatedEvent;
import com.cci.oms.domain.event.OrderStatusChangedEvent;
import com.cci.oms.domain.model.Order;
import com.cci.oms.domain.model.OrderStatus;
import com.cci.oms.domain.repository.OrderRepository;
import com.cci.oms.domain.specification.OrderSpecification;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Validated
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ApplicationEventPublisher publisher;

    @Transactional
    public OrderResponse createOrder(@Valid CreateOrderRequest request) {
        Order order = orderMapper.toEntity(request);
        order.setStatus(OrderStatus.PENDING);
        Order savedOrder = orderRepository.save(order);
        log.info("Order created with id {}", savedOrder.getId());
        publisher.publishEvent(new OrderCreatedEvent(
                savedOrder.getId(), savedOrder.getCustomerId(),
                savedOrder.getProductId(), savedOrder.getQuantity()));
        return orderMapper.toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(OrderFilterRequest filter, Pageable pageable) {
        return orderRepository.findAll(OrderSpecification.withFilter(filter), pageable)
                .map(orderMapper::toResponse);
    }

    @Cacheable(value = CacheConfig.ORDERS_CACHE, key = "#id")
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {
        log.debug("Cache miss for order {}, loading from database", id);
        return orderRepository.findById(id)
                .map(orderMapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @CachePut(value = CacheConfig.ORDERS_CACHE, key = "#id")
    @Transactional
    public OrderResponse updateOrderStatus(UUID id, OrderStatus newStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        OrderStatus previousStatus = order.getStatus();
        validateStatusTransition(previousStatus, newStatus);
        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);
        log.info("Order {} status updated {} -> {}", id, previousStatus, newStatus);
        publisher.publishEvent(new OrderStatusChangedEvent(
                id, previousStatus, newStatus, saved.getProductId(), saved.getQuantity()));
        return orderMapper.toResponse(saved);
    }

    @CacheEvict(value = CacheConfig.ORDERS_CACHE, key = "#id")
    @Transactional
    public void deleteOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        order.setDeletedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("Order {} soft-deleted", id);
    }

    // Java 21 switch expression — exhaustive, compiler-checked against the enum
    private void validateStatusTransition(OrderStatus from, OrderStatus to) {
        boolean valid = switch (from) {
            case PENDING            -> to == OrderStatus.CONFIRMED || to == OrderStatus.CANCELLED;
            case CONFIRMED          -> to == OrderStatus.SHIPPED   || to == OrderStatus.CANCELLED;
            case SHIPPED, CANCELLED -> false;
        };
        if (!valid) {
            throw new InvalidOrderStatusTransitionException(from, to);
        }
    }
}