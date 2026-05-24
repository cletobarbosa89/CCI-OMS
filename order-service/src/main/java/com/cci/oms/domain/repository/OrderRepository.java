package com.cci.oms.domain.repository;

import com.cci.oms.domain.model.Order;
import com.cci.oms.domain.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    List<Order> findByCustomerId(String customerId);

    @Query("SELECT o FROM Order o WHERE o.customerId = :customerId AND o.status = :status")
    List<Order> findByCustomerIdAndStatus(@Param("customerId") String customerId,
                                          @Param("status") OrderStatus status);

    @Query(value = "SELECT COUNT(*) FROM orders WHERE status = :status", nativeQuery = true)
    long countByStatusNative(@Param("status") String status);
}