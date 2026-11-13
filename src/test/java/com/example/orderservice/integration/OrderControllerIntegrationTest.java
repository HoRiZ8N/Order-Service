package com.example.orderservice.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class OrderControllerIntegrationTest extends AbstractIntegrationTest {

  private static final String ORDERS = "/api/v1/orders";

  @Test
  void createPersistsOrderWithUserAndRelaysToken() throws Exception {
    Long phone = insertItem("Phone", "100.00");
    Long phoneCase = insertItem("Case", "12.50");
    stubUserByEmail();

    mockMvc.perform(post(ORDERS)
            .header(HttpHeaders.AUTHORIZATION, TOKEN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson(EMAIL, List.of(line(phone, 1), line(phoneCase, 2)))))
        .andExpect(status().isCreated())
        .andExpect(header().string(HttpHeaders.LOCATION, endsWith(ORDERS + "/1")))
        .andExpect(jsonPath("$.order.id").value(1))
        .andExpect(jsonPath("$.order.userId").value(USER_ID))
        .andExpect(jsonPath("$.order.status").value("PENDING"))
        .andExpect(jsonPath("$.order.totalPrice").value(125.00))
        .andExpect(jsonPath("$.order.orderItems", hasSize(2)))
        .andExpect(jsonPath("$.order.orderItems[0].item.name").value("Phone"))
        .andExpect(jsonPath("$.order.createdAt").exists())
        .andExpect(jsonPath("$.user.id").value(USER_ID))
        .andExpect(jsonPath("$.user.email").value(EMAIL));

    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items", Long.class))
        .isEqualTo(2);
    USER_SERVICE.verify(getRequestedFor(urlPathEqualTo(BY_EMAIL_PATH))
        .withQueryParam("email", equalTo(EMAIL))
        .withHeader(HttpHeaders.AUTHORIZATION, equalTo(TOKEN)));
  }

  @Test
  void createReturnsNotFoundWhenUserMissing() throws Exception {
    Long phone = insertItem("Phone", "100.00");
    USER_SERVICE.stubFor(get(urlPathEqualTo(BY_EMAIL_PATH)).willReturn(notFound()));

    mockMvc.perform(post(ORDERS)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson("missing@example.com", List.of(line(phone, 1)))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("User with email missing@example.com not found"));

    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isZero();
  }

  @Test
  void createReturnsNotFoundWhenItemMissing() throws Exception {
    stubUserByEmail();

    mockMvc.perform(post(ORDERS)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson(EMAIL, List.of(line(99L, 1)))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Item with ids [99] not found"));
  }

  @Test
  void createRejectsInvalidBody() throws Exception {
    mockMvc.perform(post(ORDERS)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson("not-an-email", List.of(line(1L, 0)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.email").exists())
        .andExpect(jsonPath("$.errors['orderItems[0].quantity']").exists());

    assertThat(USER_SERVICE.getAllServeEvents()).isEmpty();
  }

  @Test
  void createRejectsEmptyItems() throws Exception {
    mockMvc.perform(post(ORDERS)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson(EMAIL, List.of())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.orderItems").exists());
  }

  @Test
  void createReturnsForbiddenWhenUserServiceDeniesAccess() throws Exception {
    Long phone = insertItem("Phone", "100.00");
    USER_SERVICE.stubFor(get(urlPathEqualTo(BY_EMAIL_PATH))
        .willReturn(forbidden()));

    mockMvc.perform(post(ORDERS)
            .header(HttpHeaders.AUTHORIZATION, TOKEN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson(EMAIL, List.of(line(phone, 1)))))
        .andExpect(status().isForbidden());
  }

  @Test
  void createRejectsUnsupportedContentType() throws Exception {
    mockMvc.perform(post(ORDERS).contentType(MediaType.TEXT_PLAIN).content("order"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void getByIdReturnsOrderWithUser() throws Exception {
    Long phone = insertItem("Phone", "100.00");
    Long orderId = insertOrder(USER_ID, "PENDING", phone, 2);
    stubUserById();

    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS + "/{id}", orderId)
            .header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.order.id").value(orderId))
        .andExpect(jsonPath("$.order.orderItems[0].quantity").value(2))
        .andExpect(jsonPath("$.user.email").value(EMAIL));

    USER_SERVICE.verify(getRequestedFor(urlPathEqualTo(USERS_PATH + USER_ID))
        .withHeader(HttpHeaders.AUTHORIZATION, equalTo(TOKEN)));
  }

  @Test
  void getByIdReturnsNotFound() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS + "/{id}", 999))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Order with id 999 not found"));
  }

  @Test
  void getByIdRejectsInvalidId() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS + "/{id}", 0))
        .andExpect(status().isBadRequest());
    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS + "/{id}", "abc"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getAllFiltersByStatusesAndCreationDate() throws Exception {
    Long phone = insertItem("Phone", "100.00");
    insertOrder(USER_ID, "PENDING", phone, 1);
    insertOrder(USER_ID, "CONFIRMED", phone, 1);
    insertOrder(USER_ID, "SHIPPED", phone, 1);
    jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = 3",
        LocalDateTime.now().minusDays(10));
    stubUserById();

    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS)
            .param("statuses", "CONFIRMED", "SHIPPED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.content[*].order.status").value(
            containsInAnyOrder("CONFIRMED", "SHIPPED")))
        .andExpect(jsonPath("$.content[0].user.email").value(EMAIL));

    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS)
            .param("from", LocalDateTime.now().minusDays(1).toString())
            .param("to", LocalDateTime.now().plusDays(1).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.page.totalElements").value(2));

    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS)
            .param("statuses", "SHIPPED")
            .param("from", LocalDateTime.now().minusDays(1).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)));
  }

  @Test
  void getAllPaginatesAndSorts() throws Exception {
    Long phone = insertItem("Phone", "100.00");
    insertOrder(USER_ID, "PENDING", phone, 1);
    insertOrder(USER_ID, "PENDING", phone, 1);
    insertOrder(USER_ID, "PENDING", phone, 1);
    stubUserById();

    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS)
            .param("page", "0").param("size", "2").param("sort", "id,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.content[0].order.id").value(3))
        .andExpect(jsonPath("$.page.totalElements").value(3))
        .andExpect(jsonPath("$.page.totalPages").value(2));

    USER_SERVICE.verify(1, getRequestedFor(urlPathEqualTo(USERS_PATH + USER_ID)));
  }

  @Test
  void getAllRejectsInvalidFilters() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS)
            .param("from", "2026-01-02T00:00:00").param("to", "2026-01-01T00:00:00"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Parameter 'from' must not be after 'to'"));
    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS).param("statuses", "UNKNOWN"))
        .andExpect(status().isBadRequest());
    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS).param("sort", "unknown"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getByUserIdReturnsOnlyUserOrders() throws Exception {
    Long phone = insertItem("Phone", "100.00");
    insertOrder(USER_ID, "PENDING", phone, 1);
    insertOrder(USER_ID, "CONFIRMED", phone, 1);
    insertOrder(2L, "PENDING", phone, 1);
    stubUserById();

    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS + "/users/{userId}", USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[*].order.userId").value(
            everyItem(is(USER_ID.intValue()))))
        .andExpect(jsonPath("$[0].user.email").value(EMAIL));
  }

  @Test
  void getByUserIdReturnsEmptyListWithoutCallingUserService() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS + "/users/{userId}", 42))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));

    assertThat(USER_SERVICE.getAllServeEvents()).isEmpty();
  }

  @Test
  void updateSynchronizesItemsAndRecalculatesTotal() throws Exception {
    Long phone = insertItem("Phone", "100.00");
    Long phoneCase = insertItem("Case", "12.50");
    Long cable = insertItem("Cable", "5.00");
    stubUserByEmail();
    stubUserById();
    Long orderId = createOrder(phone, 1);

    mockMvc.perform(put(ORDERS + "/{id}", orderId)
            .header(HttpHeaders.AUTHORIZATION, TOKEN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson("CONFIRMED", List.of(line(phoneCase, 4), line(cable, 2)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.order.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.order.totalPrice").value(60.00))
        .andExpect(jsonPath("$.order.orderItems", hasSize(2)))
        .andExpect(jsonPath("$.user.email").value(EMAIL));

    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "SELECT item_id, quantity FROM order_items WHERE order_id = ? ORDER BY item_id", orderId);
    assertThat(rows).extracting(row -> ((Number) row.get("item_id")).longValue(),
            row -> ((Number) row.get("quantity")).intValue())
        .containsExactly(
            tuple(phoneCase, 4),
            tuple(cable, 2));
    assertThat(jdbcTemplate.queryForObject(
        "SELECT total_price FROM orders WHERE id = ?", BigDecimal.class, orderId))
        .isEqualByComparingTo("60.00");
  }

  @Test
  void updateKeepsExistingItemRowWhenQuantityChanges() throws Exception {
    Long phone = insertItem("Phone", "100.00");
    Long orderId = insertOrder(USER_ID, "PENDING", phone, 1);
    Long orderItemId = jdbcTemplate.queryForObject(
        "SELECT id FROM order_items WHERE order_id = ?", Long.class, orderId);
    stubUserById();

    mockMvc.perform(put(ORDERS + "/{id}", orderId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson("SHIPPED", List.of(line(phone, 3)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.order.orderItems[0].id").value(orderItemId))
        .andExpect(jsonPath("$.order.orderItems[0].quantity").value(3))
        .andExpect(jsonPath("$.order.totalPrice").value(300.00));
  }

  @Test
  void updateReturnsNotFoundForMissingOrder() throws Exception {
    mockMvc.perform(put(ORDERS + "/{id}", 999)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson("SHIPPED", List.of(line(1L, 1)))))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateRejectsInvalidStatus() throws Exception {
    mockMvc.perform(put(ORDERS + "/{id}", 1)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson("LOST", List.of(line(1L, 1)))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deleteSoftDeletesOrder() throws Exception {
    Long phone = insertItem("Phone", "100.00");
    Long orderId = insertOrder(USER_ID, "PENDING", phone, 1);

    mockMvc.perform(delete(ORDERS + "/{id}", orderId))
        .andExpect(status().isNoContent());

    assertThat(jdbcTemplate.queryForObject(
        "SELECT deleted FROM orders WHERE id = ?", Boolean.class, orderId)).isTrue();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM order_items WHERE order_id = ?", Long.class, orderId)).isEqualTo(1);
    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS + "/{id}", orderId))
        .andExpect(status().isNotFound());
    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS + "/users/{userId}", USER_ID))
        .andExpect(jsonPath("$", hasSize(0)));
    mockMvc.perform(delete(ORDERS + "/{id}", orderId))
        .andExpect(status().isNotFound());
  }

  @Test
  void unknownMethodReturnsMethodNotAllowed() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.patch(ORDERS + "/{id}", 1))
        .andExpect(status().isMethodNotAllowed());
  }
}
