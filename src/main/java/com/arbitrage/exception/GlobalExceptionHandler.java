package com.arbitrage.exception;

import com.arbitrage.model.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ApiResponse<String>> handleRuntimeException(RuntimeException ex) {
    ApiResponse<String> errorResponse =
        new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage(), null);
    return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  // Catch-all handler for any other exceptions
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<String>> handleAllExceptions(Exception ex) {
    ApiResponse<String> errorResponse =
        new ApiResponse<>(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "An unexpected error occurred: " + ex.getMessage(),
            null);
    return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @ExceptionHandler(OrderNotFoundException.class)
  public ResponseEntity<ApiResponse<String>> handlePayoutNotFoundException(
      OrderNotFoundException ex) {
    ApiResponse<String> response =
        new ApiResponse<>(HttpStatus.NOT_FOUND.value(), ex.getMessage(), null);
    return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
  }
}
