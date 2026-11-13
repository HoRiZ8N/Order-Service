package com.example.orderservice.mapper;

import com.example.orderservice.dto.OrderCreateDto;
import com.example.orderservice.dto.OrderDto;
import com.example.orderservice.dto.OrderUpdateDto;
import com.example.orderservice.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(uses = OrderItemMapper.class)
public interface OrderMapper {

  OrderDto toDto(Order order);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "userId", source = "userId")
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "totalPrice", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "orderItems", ignore = true)
  Order toEntity(OrderCreateDto dto, Long userId);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "userId", ignore = true)
  @Mapping(target = "totalPrice", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "orderItems", ignore = true)
  void updateEntity(OrderUpdateDto dto, @MappingTarget Order order);
}
