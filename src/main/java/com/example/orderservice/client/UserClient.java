package com.example.orderservice.client;

import com.example.orderservice.dto.UserDto;
import com.example.orderservice.exception.ResourceNotFoundException;
import com.example.orderservice.exception.ServiceUnavailableException;
import com.example.orderservice.exception.UserServiceAccessException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class UserClient {

  private static final String USER_SERVICE = "userService";
  private static final String RESOURCE = "User";

  private final RestClient userServiceRestClient;

  @CircuitBreaker(name = USER_SERVICE, fallbackMethod = "getByIdFallback")
  public Optional<UserDto> getById(Long id) {
    return Optional.ofNullable(userServiceRestClient.get()
        .uri("/api/v1/users/{id}", id)
        .retrieve()
        .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, response) -> {
          throw new ResourceNotFoundException(RESOURCE, id);
        })
        .onStatus(UserClient::isAccessDenied, (request, response) -> {
          throw new UserServiceAccessException(response.getStatusCode());
        })
        .body(UserDto.class));
  }

  @CircuitBreaker(name = USER_SERVICE, fallbackMethod = "getByEmailFallback")
  public UserDto getByEmail(String email) {
    return userServiceRestClient.get()
        .uri(builder -> builder.path("/api/v1/users/by-email").queryParam("email", "{email}").build(email))
        .retrieve()
        .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, response) -> {
          throw new ResourceNotFoundException(RESOURCE, "email", email);
        })
        .onStatus(UserClient::isAccessDenied, (request, response) -> {
          throw new UserServiceAccessException(response.getStatusCode());
        })
        .body(UserDto.class);
  }

  private Optional<UserDto> getByIdFallback(Long id, UserServiceAccessException ex) {
    throw ex;
  }

  private Optional<UserDto> getByIdFallback(Long id, Throwable ex) {
    return Optional.empty();
  }

  private UserDto getByEmailFallback(String email, ResourceNotFoundException ex) {
    throw ex;
  }

  private UserDto getByEmailFallback(String email, UserServiceAccessException ex) {
    throw ex;
  }

  private UserDto getByEmailFallback(String email, Throwable ex) {
    throw new ServiceUnavailableException("User service");
  }

  private static boolean isAccessDenied(HttpStatusCode status) {
    return status.isSameCodeAs(HttpStatus.UNAUTHORIZED) || status.isSameCodeAs(HttpStatus.FORBIDDEN);
  }
}
