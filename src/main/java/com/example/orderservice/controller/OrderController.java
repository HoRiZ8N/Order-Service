package com.example.orderservice.controller;

import com.example.orderservice.dto.OrderCreateDto;
import com.example.orderservice.dto.OrderResponseDto;
import com.example.orderservice.dto.OrderUpdateDto;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.exception.InvalidRequestException;
import com.example.orderservice.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;

  @PostMapping
  public ResponseEntity<OrderResponseDto> create(@Valid @RequestBody OrderCreateDto dto) {
    OrderResponseDto created = orderService.create(dto);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(created.order().id())
        .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @GetMapping("/{id}")
  public ResponseEntity<OrderResponseDto> getById(@PathVariable @Positive Long id) {
    return ResponseEntity.ok(orderService.getById(id));
  }

  @GetMapping
  public ResponseEntity<PagedModel<OrderResponseDto>> getAll(
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime to,
      @RequestParam(required = false) Set<OrderStatus> statuses,
      @PageableDefault(sort = "id") Pageable pageable) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new InvalidRequestException("Parameter 'from' must not be after 'to'");
    }
    return ResponseEntity.ok(
        new PagedModel<>(orderService.getAll(from, to, statuses, pageable)));
  }

  @GetMapping("/users/{userId}")
  public ResponseEntity<List<OrderResponseDto>> getByUserId(@PathVariable @Positive Long userId) {
    return ResponseEntity.ok(orderService.getByUserId(userId));
  }

  @PutMapping("/{id}")
  public ResponseEntity<OrderResponseDto> update(
      @PathVariable @Positive Long id, @Valid @RequestBody OrderUpdateDto dto) {
    return ResponseEntity.ok(orderService.update(id, dto));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
    orderService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
