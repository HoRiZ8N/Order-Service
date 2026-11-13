package com.example.orderservice.dto;

import java.time.LocalDate;

public record UserDto(
    Long id,
    String name,
    String surname,
    LocalDate birthDate,
    String email,
    Boolean active) {}
