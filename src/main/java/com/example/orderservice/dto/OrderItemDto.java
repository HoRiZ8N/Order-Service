package com.example.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemDto(
    @JsonProperty(access = Access.READ_ONLY) Long id,
    @NotNull @Positive Long itemId,
    @JsonProperty(access = Access.READ_ONLY) ItemDto item,
    @NotNull @Positive Integer quantity) {}
