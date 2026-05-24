package com.cci.oms.domain.repository;

import com.cci.oms.application.dto.OrderFilterRequest;
import com.cci.oms.domain.model.Order;
import com.cci.oms.domain.model.OrderStatus;
import com.cci.oms.domain.specification.OrderSpecification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OrderRepositoryIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByCustomerId_returnsOnlyOrdersForThatCustomer() {
        Order order1 = persistOrder("CUST-001");
        Order order2 = persistOrder("CUST-001");
        persistOrder("CUST-002");
        entityManager.flush();
        entityManager.clear();

        List<Order> result = orderRepository.findByCustomerId("CUST-001");

        assertThat(result).hasSize(2)
                .extracting(Order::getId)
                .containsExactlyInAnyOrder(order1.getId(), order2.getId());
    }

    @Test
    void findByCustomerId_unknownCustomer_returnsEmptyList() {
        persistOrder("CUST-001");
        entityManager.flush();
        entityManager.clear();

        List<Order> result = orderRepository.findByCustomerId("UNKNOWN");

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_pageable_respectsPageSizeAndReportsTotalElements() {
        for (int i = 0; i < 5; i++) persistOrder("CUST-00" + i);
        entityManager.flush();
        entityManager.clear();

        Page<Order> page = orderRepository.findAll(PageRequest.of(0, 3));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void findById_afterSoftDelete_returnsEmpty() {
        Order order = persistOrder("CUST-001");
        entityManager.flush();
        entityManager.clear();

        // confirm it is visible before deletion
        assertThat(orderRepository.findById(order.getId())).isPresent();

        // soft-delete: set deletedAt and save
        Order toDelete = orderRepository.findById(order.getId()).orElseThrow();
        toDelete.setDeletedAt(LocalDateTime.now());
        orderRepository.save(toDelete);
        entityManager.flush();
        entityManager.clear();

        // @SQLRestriction("deleted_at IS NULL") should hide it now
        assertThat(orderRepository.findById(order.getId())).isEmpty();
    }

    @Test
    void findByCustomerId_excludesSoftDeletedOrders() {
        Order active = persistOrder("CUST-001");
        Order deleted = persistOrder("CUST-001");
        entityManager.flush();
        entityManager.clear();

        Order toDelete = orderRepository.findById(deleted.getId()).orElseThrow();
        toDelete.setDeletedAt(LocalDateTime.now());
        orderRepository.save(toDelete);
        entityManager.flush();
        entityManager.clear();

        List<Order> result = orderRepository.findByCustomerId("CUST-001");

        assertThat(result).hasSize(1)
                .extracting(Order::getId)
                .containsOnly(active.getId());
    }

    @Test
    void findByCustomerIdAndStatus_returnsMatchingOrders() {
        persistOrder("CUST-001", OrderStatus.PENDING);
        Order confirmed = persistOrderWithStatus("CUST-001", OrderStatus.CONFIRMED);
        persistOrder("CUST-002", OrderStatus.PENDING);
        entityManager.flush();
        entityManager.clear();

        List<Order> result = orderRepository.findByCustomerIdAndStatus("CUST-001", OrderStatus.CONFIRMED);

        assertThat(result).hasSize(1)
                .extracting(Order::getId)
                .containsOnly(confirmed.getId());
    }

    @Test
    void countByStatusNative_returnsCorrectCount() {
        persistOrder("CUST-001", OrderStatus.PENDING);
        persistOrder("CUST-001", OrderStatus.PENDING);
        persistOrderWithStatus("CUST-002", OrderStatus.CONFIRMED);
        entityManager.flush();
        entityManager.clear();

        long pendingCount = orderRepository.countByStatusNative("PENDING");

        assertThat(pendingCount).isEqualTo(2);
    }

    @Test
    void specification_filterByStatus_returnsOnlyMatchingStatus() {
        persistOrder("CUST-001", OrderStatus.PENDING);
        persistOrderWithStatus("CUST-001", OrderStatus.CONFIRMED);
        persistOrderWithStatus("CUST-002", OrderStatus.CONFIRMED);
        entityManager.flush();
        entityManager.clear();

        OrderFilterRequest filter = OrderFilterRequest.builder().status(OrderStatus.CONFIRMED).build();
        List<Order> result = orderRepository.findAll(OrderSpecification.withFilter(filter));

        assertThat(result).hasSize(2)
                .extracting(Order::getStatus)
                .containsOnly(OrderStatus.CONFIRMED);
    }

    @Test
    void specification_filterByCustomerIdAndStatus_returnsNarrowedResults() {
        persistOrder("CUST-001", OrderStatus.PENDING);
        persistOrderWithStatus("CUST-001", OrderStatus.CONFIRMED);
        persistOrderWithStatus("CUST-002", OrderStatus.CONFIRMED);
        entityManager.flush();
        entityManager.clear();

        OrderFilterRequest filter = OrderFilterRequest.builder()
                .status(OrderStatus.CONFIRMED)
                .customerId("CUST-001")
                .build();
        List<Order> result = orderRepository.findAll(OrderSpecification.withFilter(filter));

        assertThat(result).hasSize(1)
                .extracting(Order::getCustomerId)
                .containsOnly("CUST-001");
    }

    @Test
    void specification_emptyFilter_returnsAllOrders() {
        persistOrder("CUST-001", OrderStatus.PENDING);
        persistOrderWithStatus("CUST-002", OrderStatus.CONFIRMED);
        entityManager.flush();
        entityManager.clear();

        OrderFilterRequest filter = OrderFilterRequest.builder().build();
        List<Order> result = orderRepository.findAll(OrderSpecification.withFilter(filter));

        assertThat(result).hasSize(2);
    }

    private Order persistOrder(String customerId) {
        return persistOrder(customerId, OrderStatus.PENDING);
    }

    private Order persistOrder(String customerId, OrderStatus status) {
        Order order = Order.builder()
                .customerId(customerId)
                .productId("PROD-001")
                .quantity(1)
                .totalAmount(new BigDecimal("10.00"))
                .status(status)
                .build();
        return entityManager.persistAndFlush(order);
    }

    private Order persistOrderWithStatus(String customerId, OrderStatus status) {
        return persistOrder(customerId, status);
    }
}