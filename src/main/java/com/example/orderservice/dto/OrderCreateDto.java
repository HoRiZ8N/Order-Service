package com.example.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderCreateDto(
    @NotBlank @Email @Size(max = 255) String email,
    @NotEmpty List<@NotNull @Valid OrderItemDto> orderItems) {}
