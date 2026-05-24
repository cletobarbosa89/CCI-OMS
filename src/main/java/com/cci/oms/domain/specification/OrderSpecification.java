package com.cci.oms.domain.specification;

import com.cci.oms.application.dto.OrderFilterRequest;
import com.cci.oms.domain.model.Order;
import com.cci.oms.domain.model.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class OrderSpecification {

    private OrderSpecification() {}

    public static Specification<Order> withFilter(OrderFilterRequest filter) {
        List<Specification<Order>> specs = new ArrayList<>();

        if (filter.getStatus() != null) {
            specs.add(byStatus(filter.getStatus()));
        }
        if (filter.getCustomerId() != null && !filter.getCustomerId().isBlank()) {
            specs.add(byCustomerId(filter.getCustomerId()));
        }
        if (filter.getDateFrom() != null) {
            specs.add(createdAfter(filter.getDateFrom()));
        }
        if (filter.getDateTo() != null) {
            specs.add(createdBefore(filter.getDateTo()));
        }

        return specs.stream()
                .reduce(Specification::and)
                .orElse(alwaysTrue());
    }

    private static Specification<Order> alwaysTrue() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Order> byStatus(OrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static Specification<Order> byCustomerId(String customerId) {
        return (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    private static Specification<Order> createdAfter(LocalDateTime from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    private static Specification<Order> createdBefore(LocalDateTime to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}