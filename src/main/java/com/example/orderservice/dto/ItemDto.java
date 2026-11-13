package com.example.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ItemDto(
    @JsonProperty(access = Access.READ_ONLY) Long id,
    @NotBlank @Size(max = 255) String name,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
    @JsonProperty(access = Access.READ_ONLY) LocalDateTime createdAt,
    @JsonProperty(access = Access.READ_ONLY) LocalDateTime updatedAt) {}
