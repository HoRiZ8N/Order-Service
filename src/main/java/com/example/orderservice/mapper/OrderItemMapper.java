package com.example.orderservice.mapper;

import com.example.orderservice.dto.OrderItemDto;
import com.example.orderservice.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(uses = ItemMapper.class)
public interface OrderItemMapper {

  @Mapping(target = "itemId", source = "item.id")
  OrderItemDto toDto(OrderItem orderItem);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "order", ignore = true)
  @Mapping(target = "item", ignore = true)
  OrderItem toEntity(OrderItemDto dto);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "order", ignore = true)
  @Mapping(target = "item", ignore = true)
  void updateEntity(OrderItemDto dto, @MappingTarget OrderItem orderItem);
}
