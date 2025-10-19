package com.arbitrage.model;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiResponse<T> {

  private LocalDateTime timestamp;
  private int status;
  private String message;
  private T data;

  public ApiResponse(int status, String message, T data) {
    this.timestamp = LocalDateTime.now();
    this.status = status;
    this.message = message;
    this.data = data;
  }
}
