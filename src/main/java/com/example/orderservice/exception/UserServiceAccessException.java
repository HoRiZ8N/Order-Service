package com.example.orderservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class UserServiceAccessException extends RuntimeException {

  private final HttpStatusCode status;

  public UserServiceAccessException(HttpStatusCode status) {
    super("Access to user data denied by User service");
    this.status = status;
  }
}
