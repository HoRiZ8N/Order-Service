package com.example.orderservice.dto;

import com.example.orderservice.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDto(
    Long id,
    Long userId,
    OrderStatus status,
    BigDecimal totalPrice,
    List<OrderItemDto> orderItems,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
