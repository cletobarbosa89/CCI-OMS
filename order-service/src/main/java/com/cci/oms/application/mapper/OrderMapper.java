package com.cci.oms.application.mapper;

import com.cci.oms.application.dto.CreateOrderRequest;
import com.cci.oms.application.dto.OrderResponse;
import com.cci.oms.domain.model.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderResponse toResponse(Order order);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Order toEntity(CreateOrderRequest request);
}