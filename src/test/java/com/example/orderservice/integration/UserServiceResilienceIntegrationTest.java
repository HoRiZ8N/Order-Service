package com.example.orderservice.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class UserServiceResilienceIntegrationTest extends AbstractIntegrationTest {

  private static final String ORDERS = "/api/v1/orders";
  private static final int CALLS_TO_OPEN_CIRCUIT = 5;

  @Test
  void getByIdReturnsOrderWithoutUserWhenUserServiceFails() throws Exception {
    Long orderId = insertOrder(USER_ID, "PENDING", insertItem("Phone", "100.00"), 1);
    USER_SERVICE.stubFor(get(urlPathEqualTo(USERS_PATH + USER_ID)).willReturn(serverError()));

    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS + "/{id}", orderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.order.id").value(orderId))
        .andExpect(jsonPath("$.user").isEmpty());
  }

  @Test
  void getByIdReturnsOrderWithoutUserWhenUserServiceTimesOut() throws Exception {
    Long orderId = insertOrder(USER_ID, "PENDING", insertItem("Phone", "100.00"), 1);
    USER_SERVICE.stubFor(get(urlPathEqualTo(USERS_PATH + USER_ID))
        .willReturn(WireMock.okJson("{}").withFixedDelay(1500)));

    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS + "/{id}", orderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.order.id").value(orderId))
        .andExpect(jsonPath("$.user").isEmpty());
  }

  @Test
  void createReturnsServiceUnavailableWhenUserServiceFails() throws Exception {
    Long phone = insertItem("Phone", "100.00");
    USER_SERVICE.stubFor(get(urlPathEqualTo(BY_EMAIL_PATH)).willReturn(serverError()));

    mockMvc.perform(post(ORDERS)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson(EMAIL, List.of(line(phone, 1)))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("User service is unavailable, try again later"));

    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isZero();
  }

  @Test
  void getByIdReturnsForbiddenWhenUserServiceDeniesAccess() throws Exception {
    Long orderId = insertOrder(USER_ID, "PENDING", insertItem("Phone", "100.00"), 1);
    USER_SERVICE.stubFor(get(urlPathEqualTo(USERS_PATH + USER_ID)).willReturn(forbidden()));

    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS + "/{id}", orderId)
            .header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Access to user data denied by User service"));
  }

  @Test
  void getByIdReturnsUnauthorizedWhenUserServiceRequiresToken() throws Exception {
    Long orderId = insertOrder(USER_ID, "PENDING", insertItem("Phone", "100.00"), 1);
    USER_SERVICE.stubFor(get(urlPathEqualTo(USERS_PATH + USER_ID))
        .willReturn(WireMock.unauthorized()));

    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS + "/{id}", orderId))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void circuitOpensAfterFailuresAndStopsCallingUserService() throws Exception {
    Long orderId = insertOrder(USER_ID, "PENDING", insertItem("Phone", "100.00"), 1);
    USER_SERVICE.stubFor(get(urlPathEqualTo(USERS_PATH + USER_ID)).willReturn(serverError()));

    for (int i = 0; i < CALLS_TO_OPEN_CIRCUIT; i++) {
      mockMvc.perform(MockMvcRequestBuilders.get(ORDERS + "/{id}", orderId))
          .andExpect(status().isOk());
    }
    assertThat(userServiceCircuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
    USER_SERVICE.resetRequests();

    mockMvc.perform(MockMvcRequestBuilders.get(ORDERS + "/{id}", orderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.order.id").value(orderId))
        .andExpect(jsonPath("$.user").isEmpty());
    mockMvc.perform(post(ORDERS)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson(EMAIL, List.of(line(1L, 1)))))
        .andExpect(status().isServiceUnavailable());

    assertThat(USER_SERVICE.getAllServeEvents()).isEmpty();
  }

  @Test
  void circuitClosesAgainWhenUserServiceRecovers() throws Exception {
    Long orderId = insertOrder(USER_ID, "PENDING", insertItem("Phone", "100.00"), 1);
    userServiceCircuitBreaker().transitionToOpenState();
    userServiceCircuitBreaker().transitionToHalfOpenState();
    stubUserById();

    for (int i = 0; i < 3; i++) {
      mockMvc.perform(MockMvcRequestBuilders.get(ORDERS + "/{id}", orderId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.user.email").value(EMAIL));
    }

    assertThat(userServiceCircuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
  }

  @Test
  void clientErrorsDoNotOpenCircuit() throws Exception {
    Long phone = insertItem("Phone", "100.00");
    Long orderId = insertOrder(USER_ID, "PENDING", phone, 1);
    USER_SERVICE.stubFor(get(urlPathEqualTo(BY_EMAIL_PATH)).willReturn(notFound()));
    USER_SERVICE.stubFor(get(urlPathEqualTo(USERS_PATH + USER_ID)).willReturn(forbidden()));

    for (int i = 0; i < CALLS_TO_OPEN_CIRCUIT; i++) {
      mockMvc.perform(post(ORDERS)
              .contentType(MediaType.APPLICATION_JSON)
              .content(createJson(EMAIL, List.of(line(phone, 1)))))
          .andExpect(status().isNotFound());
      mockMvc.perform(MockMvcRequestBuilders.get(ORDERS + "/{id}", orderId))
          .andExpect(status().isForbidden());
    }

    assertThat(userServiceCircuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    USER_SERVICE.verify(CALLS_TO_OPEN_CIRCUIT, getRequestedFor(urlPathEqualTo(BY_EMAIL_PATH))
        .withQueryParam("email", equalTo(EMAIL)));
  }
}
