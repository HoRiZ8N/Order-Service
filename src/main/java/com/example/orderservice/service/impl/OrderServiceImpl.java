package com.example.orderservice.service.impl;

import com.example.orderservice.client.UserClient;
import com.example.orderservice.dto.OrderCreateDto;
import com.example.orderservice.dto.OrderItemDto;
import com.example.orderservice.dto.OrderResponseDto;
import com.example.orderservice.dto.OrderUpdateDto;
import com.example.orderservice.dto.UserDto;
import com.example.orderservice.entity.Item;
import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderItem;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.exception.ResourceNotFoundException;
import com.example.orderservice.mapper.OrderItemMapper;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.specification.OrderSpecifications;
import com.example.orderservice.service.OrderService;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

  private static final String RESOURCE = "Order";

  private final OrderRepository orderRepository;
  private final OrderMapper orderMapper;
  private final OrderItemMapper orderItemMapper;
  private final UserClient userClient;

  @Override
  @Transactional
  public OrderResponseDto create(OrderCreateDto dto) {
    UserDto user = userClient.getByEmail(dto.email());
    Order order = orderMapper.toEntity(dto, user.id());
    applyOrderItems(order, dto.orderItems());
    return toResponse(orderRepository.saveAndFlush(order), user);
  }

  @Override
  public OrderResponseDto getById(Long id) {
    Order order = findOrder(id);
    return toResponse(order, userClient.getById(order.getUserId()).orElse(null));
  }

  @Override
  public Page<OrderResponseDto> getAll(
      LocalDateTime from, LocalDateTime to, Set<OrderStatus> statuses, Pageable pageable) {
    Page<Order> orders = orderRepository.findAll(
        OrderSpecifications.byFilter(from, to, statuses), pageable);
    Map<Long, Optional<UserDto>> users = new HashMap<>();
    return orders.map(order -> toResponse(order,
        users.computeIfAbsent(order.getUserId(), userClient::getById).orElse(null)));
  }

  @Override
  public List<OrderResponseDto> getByUserId(Long userId) {
    List<Order> orders = orderRepository.findAllByUserId(userId);
    if (orders.isEmpty()) {
      return List.of();
    }
    UserDto user = userClient.getById(userId).orElse(null);
    return orders.stream()
        .map(order -> toResponse(order, user))
        .toList();
  }

  @Override
  @Transactional
  public OrderResponseDto update(Long id, OrderUpdateDto dto) {
    Order order = findOrder(id);
    orderMapper.updateEntity(dto, order);
    applyOrderItems(order, dto.orderItems());
    Order saved = orderRepository.saveAndFlush(order);
    return toResponse(saved, userClient.getById(saved.getUserId()).orElse(null));
  }

  @Override
  @Transactional
  public void delete(Long id) {
    if (orderRepository.softDeleteById(id) == 0) {
      throw new ResourceNotFoundException(RESOURCE, id);
    }
  }

  private Order findOrder(Long id) {
    return orderRepository.findWithOrderItemsById(id)
        .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
  }

  private void applyOrderItems(Order order, List<OrderItemDto> dtos) {
    Map<Long, OrderItemDto> requested = dtos.stream().collect(Collectors.toMap(
        OrderItemDto::itemId, Function.identity(), OrderServiceImpl::mergeQuantities,
        LinkedHashMap::new));
    Map<Long, Item> items = findItems(requested.keySet());
    order.getOrderItems().removeIf(orderItem -> !requested.containsKey(orderItem.getItem().getId()));
    Map<Long, OrderItem> existing = order.getOrderItems().stream()
        .collect(Collectors.toMap(orderItem -> orderItem.getItem().getId(), Function.identity()));
    requested.forEach((itemId, dto) -> {
      OrderItem orderItem = existing.get(itemId);
      if (orderItem != null) {
        orderItemMapper.updateEntity(dto, orderItem);
        return;
      }
      OrderItem created = orderItemMapper.toEntity(dto);
      created.setItem(items.get(itemId));
      order.addOrderItem(created);
    });
    order.recalculateTotalPrice();
  }

  private Map<Long, Item> findItems(Collection<Long> ids) {
    Map<Long, Item> items = orderRepository.findItemsByIdIn(ids).stream()
        .collect(Collectors.toMap(Item::getId, Function.identity()));
    List<Long> missing = ids.stream()
        .filter(id -> !items.containsKey(id))
        .toList();
    if (!missing.isEmpty()) {
      throw new ResourceNotFoundException("Item", "ids", missing);
    }
    return items;
  }

  private OrderResponseDto toResponse(Order order, UserDto user) {
    return new OrderResponseDto(orderMapper.toDto(order), user);
  }

  private static OrderItemDto mergeQuantities(OrderItemDto first, OrderItemDto second) {
    return new OrderItemDto(null, first.itemId(), null, first.quantity() + second.quantity());
  }
}
