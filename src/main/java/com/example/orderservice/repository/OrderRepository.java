package com.example.orderservice.repository;

import com.example.orderservice.entity.Item;
import com.example.orderservice.entity.Order;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

  @EntityGraph(attributePaths = {"orderItems", "orderItems.item"})
  Optional<Order> findWithOrderItemsById(Long id);

  @EntityGraph(attributePaths = {"orderItems", "orderItems.item"})
  List<Order> findAllByUserId(Long userId);

  @Query("select i from Item i where i.id in :ids")
  List<Item> findItemsByIdIn(@Param("ids") Collection<Long> ids);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update Order o set o.deleted = true, o.updatedAt = local datetime where o.id = :id and o.deleted = false")
  int softDeleteById(@Param("id") Long id);
}
