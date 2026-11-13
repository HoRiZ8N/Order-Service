package com.example.orderservice.exception;

import com.example.orderservice.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponseDto> handleNotFound(
      ResourceNotFoundException ex, HttpServletRequest request) {
    return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, Map.of());
  }

  @ExceptionHandler(ServiceUnavailableException.class)
  public ResponseEntity<ErrorResponseDto> handleServiceUnavailable(
      ServiceUnavailableException ex, HttpServletRequest request) {
    return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request, Map.of());
  }

  @ExceptionHandler(UserServiceAccessException.class)
  public ResponseEntity<ErrorResponseDto> handleUserServiceAccess(
      UserServiceAccessException ex, HttpServletRequest request) {
    return build(HttpStatus.valueOf(ex.getStatus().value()), ex.getMessage(), request, Map.of());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponseDto> handleDataIntegrity(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    return build(HttpStatus.CONFLICT, "Request conflicts with existing data", request, Map.of());
  }

  @ExceptionHandler(InvalidRequestException.class)
  public ResponseEntity<ErrorResponseDto> handleInvalidRequest(
      InvalidRequestException ex, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponseDto> handleBodyValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors.putIfAbsent(error.getField(), error.getDefaultMessage());
    }
    return build(HttpStatus.BAD_REQUEST, "Validation failed", request, errors);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ErrorResponseDto> handleParameterValidation(
      HandlerMethodValidationException ex, HttpServletRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();
    for (ParameterErrors result : ex.getBeanResults()) {
      result.getFieldErrors().forEach(
          error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    }
    for (ParameterValidationResult result : ex.getValueResults()) {
      errors.putIfAbsent(
          result.getMethodParameter().getParameterName(),
          result.getResolvableErrors().getFirst().getDefaultMessage());
    }
    return build(HttpStatus.BAD_REQUEST, "Validation failed", request, errors);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponseDto> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST,
        "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'",
        request, Map.of());
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponseDto> handleMissingParameter(
      MissingServletRequestParameterException ex, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, Map.of());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponseDto> handleUnreadableBody(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, "Malformed request body", request, Map.of());
  }

  @ExceptionHandler(PropertyReferenceException.class)
  public ResponseEntity<ErrorResponseDto> handleInvalidSort(
      PropertyReferenceException ex, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST,
        "Unknown sort property '" + ex.getPropertyName() + "'", request, Map.of());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponseDto> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
    return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request, Map.of());
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponseDto> handleMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
    return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), request, Map.of());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponseDto> handleNoResource(
      NoResourceFoundException ex, HttpServletRequest request) {
    return build(HttpStatus.NOT_FOUND, "Endpoint not found", request, Map.of());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponseDto> handleUnexpected(
      Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", request, Map.of());
  }

  private ResponseEntity<ErrorResponseDto> build(
      HttpStatus status, String message, HttpServletRequest request, Map<String, String> errors) {
    ErrorResponseDto body = new ErrorResponseDto(
        LocalDateTime.now(),
        status.value(),
        status.getReasonPhrase(),
        message,
        request.getRequestURI(),
        errors);
    return ResponseEntity.status(status).body(body);
  }
}
