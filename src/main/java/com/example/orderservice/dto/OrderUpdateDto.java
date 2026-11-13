package com.example.orderservice.dto;

import com.example.orderservice.entity.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record OrderUpdateDto(
    @NotNull OrderStatus status,
    @NotEmpty List<@NotNull @Valid OrderItemDto> orderItems) {}
