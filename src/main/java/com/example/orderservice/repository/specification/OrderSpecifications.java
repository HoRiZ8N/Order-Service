package com.example.orderservice.repository.specification;

import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;

public final class OrderSpecifications {

  private OrderSpecifications() {
  }

  public static Specification<Order> createdFrom(LocalDateTime from) {
    return (root, query, cb) -> from != null
        ? cb.greaterThanOrEqualTo(root.get("createdAt"), from)
        : cb.conjunction();
  }

  public static Specification<Order> createdTo(LocalDateTime to) {
    return (root, query, cb) -> to != null
        ? cb.lessThanOrEqualTo(root.get("createdAt"), to)
        : cb.conjunction();
  }

  public static Specification<Order> hasStatusIn(Collection<OrderStatus> statuses) {
    return (root, query, cb) -> !CollectionUtils.isEmpty(statuses)
        ? root.get("status").in(statuses)
        : cb.conjunction();
  }

  public static Specification<Order> byFilter(
      LocalDateTime from, LocalDateTime to, Collection<OrderStatus> statuses) {
    return Specification.allOf(createdFrom(from), createdTo(to), hasStatusIn(statuses));
  }
}
