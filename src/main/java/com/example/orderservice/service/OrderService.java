package com.example.orderservice.service;

import com.example.orderservice.dto.OrderCreateDto;
import com.example.orderservice.dto.OrderResponseDto;
import com.example.orderservice.dto.OrderUpdateDto;
import com.example.orderservice.entity.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

  OrderResponseDto create(OrderCreateDto dto);

  OrderResponseDto getById(Long id);

  Page<OrderResponseDto> getAll(
      LocalDateTime from, LocalDateTime to, Set<OrderStatus> statuses, Pageable pageable);

  List<OrderResponseDto> getByUserId(Long userId);

  OrderResponseDto update(Long id, OrderUpdateDto dto);

  void delete(Long id);
}
