package com.example.orderservice.exception;

public class ServiceUnavailableException extends RuntimeException {

  public ServiceUnavailableException(String service) {
    super(service + " is unavailable, try again later");
  }
}
