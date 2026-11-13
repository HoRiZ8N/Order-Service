package com.example.orderservice.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.JsonPath;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractIntegrationTest {

  protected static final String TOKEN = "Bearer test-token";
  protected static final Long USER_ID = 1L;
  protected static final String EMAIL = "john@example.com";
  protected static final String USERS_PATH = "/api/v1/users/";
  protected static final String BY_EMAIL_PATH = "/api/v1/users/by-email";

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  protected static final WireMockServer USER_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());

  static {
    POSTGRES.start();
    USER_SERVICE.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("app.user-service.url", USER_SERVICE::baseUrl);
    registry.add("spring.http.client.read-timeout", () -> "1s");
  }

  @Autowired
  protected MockMvc mockMvc;

  @Autowired
  protected JdbcTemplate jdbcTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private CircuitBreakerRegistry circuitBreakerRegistry;

  @BeforeEach
  void cleanUp() {
    jdbcTemplate.execute("TRUNCATE TABLE order_items, orders, items RESTART IDENTITY CASCADE");
    USER_SERVICE.resetAll();
    userServiceCircuitBreaker().reset();
  }

  protected CircuitBreaker userServiceCircuitBreaker() {
    return circuitBreakerRegistry.circuitBreaker("userService");
  }

  protected Long insertItem(String name, String price) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO items (name, price, created_at, updated_at) "
            + "VALUES (?, ?, LOCALTIMESTAMP, LOCALTIMESTAMP) RETURNING id",
        Long.class, name, new BigDecimal(price));
  }

  protected Long insertOrder(Long userId, String status, Long itemId, int quantity) {
    Long orderId = jdbcTemplate.queryForObject(
        "INSERT INTO orders (user_id, status, total_price, created_at, updated_at) "
            + "VALUES (?, ?, 0, LOCALTIMESTAMP, LOCALTIMESTAMP) RETURNING id",
        Long.class, userId, status);
    jdbcTemplate.update(
        "INSERT INTO order_items (order_id, item_id, quantity, created_at, updated_at) "
            + "VALUES (?, ?, ?, LOCALTIMESTAMP, LOCALTIMESTAMP)",
        orderId, itemId, quantity);
    return orderId;
  }

  protected void stubUserById() {
    USER_SERVICE.stubFor(get(urlPathEqualTo(USERS_PATH + USER_ID))
        .willReturn(okJson(userJson())));
  }

  protected void stubUserByEmail() {
    USER_SERVICE.stubFor(get(urlPathEqualTo(BY_EMAIL_PATH))
        .withQueryParam("email", equalTo(EMAIL))
        .willReturn(okJson(userJson())));
  }

  protected Long createOrder(Long itemId, int quantity) throws Exception {
    String body = mockMvc.perform(post("/api/v1/orders")
            .header(HttpHeaders.AUTHORIZATION, TOKEN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson(EMAIL, List.of(line(itemId, quantity)))))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return ((Number) JsonPath.read(body, "$.order.id")).longValue();
  }

  protected String createJson(String email, List<Map<String, Object>> orderItems)
      throws Exception {
    return objectMapper.writeValueAsString(Map.of("email", email, "orderItems", orderItems));
  }

  protected String updateJson(String status, List<Map<String, Object>> orderItems)
      throws Exception {
    return objectMapper.writeValueAsString(Map.of("status", status, "orderItems", orderItems));
  }

  protected static Map<String, Object> line(Long itemId, int quantity) {
    return Map.of("itemId", itemId, "quantity", quantity);
  }

  private static String userJson() {
    return """
        {"id": %d, "name": "John", "surname": "Doe", "birthDate": "1990-01-01",
         "email": "%s", "active": true, "cards": []}
        """.formatted(USER_ID, EMAIL);
  }
}
