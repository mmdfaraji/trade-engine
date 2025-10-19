package com.arbitrage.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {

  private final HttpStatus httpStatus;
  private final String errorCode;
  //  private String message;
  public AppException(String message) {
    super(message);
    this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR; // Default status
    this.errorCode = "UNKNOWN_ERROR"; // Default error code
  }

  public AppException(String message, Exception e) {
    super(message, e);
    this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR; // Default status
    this.errorCode = "UNKNOWN_ERROR"; // Default error code
  }

  public AppException(String message, HttpStatus httpStatus) {
    super(message);
    this.httpStatus = httpStatus;
    this.errorCode = "UNKNOWN_ERROR"; // Default error code
  }

  public AppException(String message, String errorCode) {
    super(message);
    this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR; // Default status
    this.errorCode = errorCode;
  }

  public AppException(String message, HttpStatus httpStatus, String errorCode) {
    super(message);
    this.httpStatus = httpStatus;
    this.errorCode = errorCode;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();

    builder.append("message: ").append(this.getMessage()).append(", ");
    builder.append("debugMessage: ").append(this.getCause().getMessage());

    return builder.toString();
  }
}
