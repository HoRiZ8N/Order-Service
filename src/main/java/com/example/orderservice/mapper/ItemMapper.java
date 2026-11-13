package com.example.orderservice.mapper;

import com.example.orderservice.dto.ItemDto;
import com.example.orderservice.entity.Item;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface ItemMapper {

  ItemDto toDto(Item item);

  @Mapping(target = "id", ignore = true)
  Item toEntity(ItemDto dto);
}
