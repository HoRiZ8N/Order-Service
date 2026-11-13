package com.example.orderservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.orderservice.client.UserClient;
import com.example.orderservice.dto.OrderCreateDto;
import com.example.orderservice.dto.OrderDto;
import com.example.orderservice.dto.OrderItemDto;
import com.example.orderservice.dto.OrderResponseDto;
import com.example.orderservice.dto.OrderUpdateDto;
import com.example.orderservice.dto.UserDto;
import com.example.orderservice.entity.Item;
import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderItem;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.exception.ResourceNotFoundException;
import com.example.orderservice.exception.ServiceUnavailableException;
import com.example.orderservice.mapper.OrderItemMapper;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplTest {

  private static final Long ORDER_ID = 10L;
  private static final Long USER_ID = 1L;
  private static final String EMAIL = "john@example.com";

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private OrderMapper orderMapper;

  @Mock
  private OrderItemMapper orderItemMapper;

  @Mock
  private UserClient userClient;

  @InjectMocks
  private OrderServiceImpl orderService;

  private final Item phone = item(1L, "Phone", "100.00");
  private final Item phoneCase = item(2L, "Case", "12.50");
  private final Item cable = item(3L, "Cable", "5.00");

  @BeforeEach
  void setUpMappers() {
    when(orderItemMapper.toEntity(any())).thenAnswer(invocation -> {
      OrderItemDto dto = invocation.getArgument(0);
      OrderItem orderItem = new OrderItem();
      orderItem.setQuantity(dto.quantity());
      return orderItem;
    });
    doAnswer(invocation -> {
      OrderItemDto dto = invocation.getArgument(0);
      OrderItem orderItem = invocation.getArgument(1);
      orderItem.setQuantity(dto.quantity());
      return null;
    }).when(orderItemMapper).updateEntity(any(), any());
    when(orderMapper.toDto(any())).thenAnswer(invocation -> toDto(invocation.getArgument(0)));
    when(orderRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void createSavesOrderForUserFoundByEmail() {
    OrderCreateDto request = new OrderCreateDto(EMAIL, List.of(line(1L, 1), line(2L, 2)));
    UserDto user = user();
    when(userClient.getByEmail(EMAIL)).thenReturn(user);
    when(orderMapper.toEntity(request, USER_ID)).thenReturn(newOrder());
    when(orderRepository.findItemsByIdIn(anyCollection())).thenReturn(List.of(phone, phoneCase));

    OrderResponseDto result = orderService.create(request);

    ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getValue().getOrderItems())
        .allSatisfy(orderItem -> assertThat(orderItem.getOrder()).isSameAs(saved.getValue()))
        .extracting(orderItem -> orderItem.getItem().getId(), OrderItem::getQuantity)
        .containsExactly(
            tuple(1L, 1),
            tuple(2L, 2));
    assertThat(saved.getValue().getTotalPrice()).isEqualByComparingTo("125.00");
    assertThat(result.user()).isEqualTo(user);
    assertThat(result.order().totalPrice()).isEqualByComparingTo("125.00");
  }

  @Test
  void createMergesQuantitiesOfDuplicateItems() {
    OrderCreateDto request = new OrderCreateDto(EMAIL, List.of(line(2L, 1), line(2L, 3)));
    when(userClient.getByEmail(EMAIL)).thenReturn(user());
    when(orderMapper.toEntity(request, USER_ID)).thenReturn(newOrder());
    when(orderRepository.findItemsByIdIn(anyCollection())).thenReturn(List.of(phoneCase));

    OrderResponseDto result = orderService.create(request);

    assertThat(result.order().orderItems()).singleElement()
        .satisfies(orderItem -> assertThat(orderItem.quantity()).isEqualTo(4));
    assertThat(result.order().totalPrice()).isEqualByComparingTo("50.00");
  }

  @Test
  void createThrowsWhenItemMissing() {
    OrderCreateDto request = new OrderCreateDto(EMAIL, List.of(line(1L, 1), line(99L, 1)));
    when(userClient.getByEmail(EMAIL)).thenReturn(user());
    when(orderMapper.toEntity(request, USER_ID)).thenReturn(newOrder());
    when(orderRepository.findItemsByIdIn(anyCollection())).thenReturn(List.of(phone));

    assertThatThrownBy(() -> orderService.create(request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("[99]");
    verify(orderRepository, never()).saveAndFlush(any());
  }

  @Test
  void createPropagatesUserNotFound() {
    OrderCreateDto request = new OrderCreateDto(EMAIL, List.of(line(1L, 1)));
    when(userClient.getByEmail(EMAIL))
        .thenThrow(new ResourceNotFoundException("User", "email", EMAIL));

    assertThatThrownBy(() -> orderService.create(request))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(orderRepository);
  }

  @Test
  void createPropagatesUserServiceUnavailable() {
    OrderCreateDto request = new OrderCreateDto(EMAIL, List.of(line(1L, 1)));
    when(userClient.getByEmail(EMAIL)).thenThrow(new ServiceUnavailableException("User service"));

    assertThatThrownBy(() -> orderService.create(request))
        .isInstanceOf(ServiceUnavailableException.class);
    verifyNoInteractions(orderRepository);
  }

  @Test
  void getByIdReturnsOrderWithUser() {
    Order order = existingOrder(orderItem(phone, 1));
    when(orderRepository.findWithOrderItemsById(ORDER_ID)).thenReturn(Optional.of(order));
    when(userClient.getById(USER_ID)).thenReturn(Optional.of(user()));

    OrderResponseDto result = orderService.getById(ORDER_ID);

    assertThat(result.order().id()).isEqualTo(ORDER_ID);
    assertThat(result.user()).isEqualTo(user());
  }

  @Test
  void getByIdReturnsOrderWithoutUserWhenUserUnavailable() {
    when(orderRepository.findWithOrderItemsById(ORDER_ID))
        .thenReturn(Optional.of(existingOrder(orderItem(phone, 1))));
    when(userClient.getById(USER_ID)).thenReturn(Optional.empty());

    OrderResponseDto result = orderService.getById(ORDER_ID);

    assertThat(result.order().id()).isEqualTo(ORDER_ID);
    assertThat(result.user()).isNull();
  }

  @Test
  void getByIdThrowsWhenOrderMissing() {
    when(orderRepository.findWithOrderItemsById(ORDER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.getById(ORDER_ID))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(userClient);
  }

  @Test
  void getAllMapsPageAndFetchesEachUserOnce() {
    Pageable pageable = PageRequest.of(0, 10);
    Order first = existingOrder(orderItem(phone, 1));
    Order second = existingOrder(orderItem(cable, 2));
    second.setId(ORDER_ID + 1);
    when(orderRepository.findAll(ArgumentMatchers.<Specification<Order>>any(), eq(pageable)))
        .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
    when(userClient.getById(USER_ID)).thenReturn(Optional.of(user()));

    Page<OrderResponseDto> result = orderService.getAll(
        LocalDateTime.now().minusDays(1), LocalDateTime.now(), Set.of(OrderStatus.PENDING), pageable);

    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getContent())
        .extracting(response -> response.order().id())
        .containsExactly(ORDER_ID, ORDER_ID + 1);
    assertThat(result.getContent()).allSatisfy(response -> assertThat(response.user()).isEqualTo(user()));
    verify(userClient, times(1)).getById(USER_ID);
  }

  @Test
  void getByUserIdReturnsOrdersWithUser() {
    when(orderRepository.findAllByUserId(USER_ID))
        .thenReturn(List.of(existingOrder(orderItem(phone, 1))));
    when(userClient.getById(USER_ID)).thenReturn(Optional.of(user()));

    List<OrderResponseDto> result = orderService.getByUserId(USER_ID);

    assertThat(result).singleElement()
        .satisfies(response -> assertThat(response.user()).isEqualTo(user()));
  }

  @Test
  void getByUserIdReturnsEmptyListWithoutCallingUserService() {
    when(orderRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

    assertThat(orderService.getByUserId(USER_ID)).isEmpty();
    verifyNoInteractions(userClient);
  }

  @Test
  void updateChangesStatusAndSynchronizesItems() {
    Order order = existingOrder(orderItem(phone, 1), orderItem(phoneCase, 1));
    OrderUpdateDto request = new OrderUpdateDto(OrderStatus.CONFIRMED,
        List.of(line(2L, 4), line(3L, 2)));
    when(orderRepository.findWithOrderItemsById(ORDER_ID)).thenReturn(Optional.of(order));
    doAnswer(invocation -> {
      Order target = invocation.getArgument(1);
      target.setStatus(request.status());
      return null;
    }).when(orderMapper).updateEntity(request, order);
    when(orderRepository.findItemsByIdIn(anyCollection())).thenReturn(List.of(phoneCase, cable));
    when(userClient.getById(USER_ID)).thenReturn(Optional.of(user()));

    OrderResponseDto result = orderService.update(ORDER_ID, request);

    verify(orderRepository).saveAndFlush(order);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getOrderItems())
        .extracting(orderItem -> orderItem.getItem().getId(), OrderItem::getQuantity)
        .containsExactlyInAnyOrder(
            tuple(2L, 4),
            tuple(3L, 2));
    assertThat(order.getTotalPrice()).isEqualByComparingTo("60.00");
    assertThat(result.user()).isEqualTo(user());
  }

  @Test
  void updateThrowsWhenOrderMissing() {
    when(orderRepository.findWithOrderItemsById(ORDER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.update(ORDER_ID,
        new OrderUpdateDto(OrderStatus.SHIPPED, List.of(line(1L, 1)))))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(orderRepository, never()).saveAndFlush(any());
  }

  @Test
  void updateThrowsWhenItemMissing() {
    when(orderRepository.findWithOrderItemsById(ORDER_ID))
        .thenReturn(Optional.of(existingOrder(orderItem(phone, 1))));
    when(orderRepository.findItemsByIdIn(anyCollection())).thenReturn(List.of());

    assertThatThrownBy(() -> orderService.update(ORDER_ID,
        new OrderUpdateDto(OrderStatus.SHIPPED, List.of(line(99L, 1)))))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(orderRepository, never()).saveAndFlush(any());
  }

  @Test
  void deleteSoftDeletesOrder() {
    when(orderRepository.softDeleteById(ORDER_ID)).thenReturn(1);

    orderService.delete(ORDER_ID);

    verify(orderRepository).softDeleteById(ORDER_ID);
    verify(orderRepository, never()).deleteById(anyLong());
  }

  @Test
  void deleteThrowsWhenOrderMissing() {
    when(orderRepository.softDeleteById(ORDER_ID)).thenReturn(0);

    assertThatThrownBy(() -> orderService.delete(ORDER_ID))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  private static OrderItemDto line(Long itemId, int quantity) {
    return new OrderItemDto(null, itemId, null, quantity);
  }

  private static Item item(Long id, String name, String price) {
    Item item = new Item();
    item.setId(id);
    item.setName(name);
    item.setPrice(new BigDecimal(price));
    return item;
  }

  private static OrderItem orderItem(Item item, int quantity) {
    OrderItem orderItem = new OrderItem();
    orderItem.setItem(item);
    orderItem.setQuantity(quantity);
    return orderItem;
  }

  private static Order newOrder() {
    Order order = new Order();
    order.setUserId(USER_ID);
    return order;
  }

  private static Order existingOrder(OrderItem... orderItems) {
    Order order = newOrder();
    order.setId(ORDER_ID);
    for (OrderItem orderItem : orderItems) {
      order.addOrderItem(orderItem);
    }
    order.recalculateTotalPrice();
    return order;
  }

  private static UserDto user() {
    return new UserDto(USER_ID, "John", "Doe", LocalDate.of(1990, 1, 1), EMAIL, true);
  }

  private static OrderDto toDto(Order order) {
    List<OrderItemDto> orderItems = order.getOrderItems().stream()
        .map(orderItem -> new OrderItemDto(
            orderItem.getId(), orderItem.getItem().getId(), null, orderItem.getQuantity()))
        .toList();
    return new OrderDto(order.getId(), order.getUserId(), order.getStatus(),
        order.getTotalPrice(), orderItems, null, null);
  }
}
