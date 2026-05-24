package com.cci.oms.application.service;

import com.cci.oms.application.config.CacheConfig;
import com.cci.oms.application.config.TestCacheConfig;
import com.cci.oms.application.dto.OrderResponse;
import com.cci.oms.domain.model.Order;
import com.cci.oms.domain.model.OrderStatus;
import com.cci.oms.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestCacheConfig.class)
class OrderServiceCachingTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        Cache cache = cacheManager.getCache(CacheConfig.ORDERS_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void getOrderById_firstCall_populatesCache() {
        Order saved = orderRepository.save(buildOrder());

        orderService.getOrderById(saved.getId());

        Cache.ValueWrapper cached = cacheManager.getCache(CacheConfig.ORDERS_CACHE).get(saved.getId());
        assertThat(cached).isNotNull();
        assertThat(((OrderResponse) cached.get()).id()).isEqualTo(saved.getId());
    }

    @Test
    void getOrderById_secondCall_returnsCachedValue() {
        Order saved = orderRepository.save(buildOrder());

        OrderResponse first = orderService.getOrderById(saved.getId());
        OrderResponse second = orderService.getOrderById(saved.getId());

        assertThat(second).isEqualTo(first);
        assertThat(cacheManager.getCache(CacheConfig.ORDERS_CACHE).get(saved.getId())).isNotNull();
    }

    @Test
    void updateOrderStatus_updatesCacheWithNewValue() {
        Order saved = orderRepository.save(buildOrder());
        orderService.getOrderById(saved.getId()); // populate cache

        // Use CANCELLED (terminal state) so the saga does not trigger further transitions
        orderService.updateOrderStatus(saved.getId(), OrderStatus.CANCELLED);

        Cache.ValueWrapper cached = cacheManager.getCache(CacheConfig.ORDERS_CACHE).get(saved.getId());
        assertThat(cached).isNotNull();
        assertThat(((OrderResponse) cached.get()).status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void deleteOrder_evictsCacheEntry() {
        Order saved = orderRepository.save(buildOrder());
        orderService.getOrderById(saved.getId()); // populate cache
        assertThat(cacheManager.getCache(CacheConfig.ORDERS_CACHE).get(saved.getId())).isNotNull();

        orderService.deleteOrder(saved.getId());

        assertThat(cacheManager.getCache(CacheConfig.ORDERS_CACHE).get(saved.getId())).isNull();
    }

    private Order buildOrder() {
        return Order.builder()
                .customerId("CUST-CACHE-" + UUID.randomUUID())
                .productId("PROD-001")
                .quantity(1)
                .totalAmount(new BigDecimal("9.99"))
                .status(OrderStatus.PENDING)
                .build();
    }
}