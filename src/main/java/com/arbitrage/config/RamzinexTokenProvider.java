package com.arbitrage.config;

import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import com.arbitrage.service.ExchangeAccessService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class RamzinexTokenProvider {

  private final ExchangeAccessService access;

  private final RestClient openClient;

  private final AtomicReference<CachedToken> cache = new AtomicReference<>();

  public RamzinexTokenProvider(ExchangeAccessService access, RestClient ramzinexOpenRestClient) {
    this.access = access;
    this.openClient = ramzinexOpenRestClient;
  }

  public String getToken(String exchangeName) {
    CachedToken ct = cache.get();
    long now = Instant.now().getEpochSecond();
    if (ct != null && ct.expiresAtEpochSec - 30 > now) return ct.token;

    synchronized (this) {
      ct = cache.get();
      if (ct != null && ct.expiresAtEpochSec - 30 > now) return ct.token;

      Exchange ex = access.requireExchange(exchangeName);
      ExchangeAccount acc = access.requireAccount(exchangeName, "Ramzinex");
      String apiKey = acc.getApiKey();
      String secret = acc.getSecretKey();

      Map<String, Object> body = Map.of("api_key", apiKey, "secret", secret);

      int attempts = 0;
      while (true) {
        attempts++;
        try {
          Map<?, ?> response =
              openClient
                  .post()
                  .uri("/exchange/api/v1.0/exchange/auth/api_key/getToken")
                  .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                  .body(body)
                  .retrieve()
                  .body(Map.class);

          if (response == null
              || !(response.get("data") instanceof Map)
              || ((Map) response.get("data")).get("token") == null) {
            throw new IllegalStateException("Ramzinex getToken: empty/invalid response");
          }

          String token = String.valueOf(((Map) response.get("data")).get("token"));
          long exp = decodeJwtExp(token);
          cache.set(new CachedToken(token, exp));
          return token;

        } catch (ResourceAccessException rae) {
          // Root cause can be InterruptedException; handle it distinctly
          Throwable cause = rae.getCause();
          if (cause instanceof java.lang.InterruptedException) {
            Thread.currentThread().interrupt(); // restore
            throw new IllegalStateException("Ramzinex getToken interrupted", cause);
          }
          if (attempts >= 3) throw rae;
          try {
            Thread.sleep(250L * attempts);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ramzinex getToken interrupted during backoff", ie);
          }
        }
      }
    }
  }

  private static final class CachedToken {
    final String token;
    final long expiresAtEpochSec;

    private CachedToken(String token, long exp) {
      this.token = token;
      this.expiresAtEpochSec = exp;
    }
  }

  @SuppressWarnings("unchecked")
  private static String extractToken(Map response) {
    if (response == null) throw new IllegalStateException("getToken: response is null");
    Object dataObj = response.get("data");
    if (!(dataObj instanceof Map)) throw new IllegalStateException("getToken: data missing");
    Object tokenObj = ((Map) dataObj).get("token");
    if (!(tokenObj instanceof String) || ((String) tokenObj).isBlank())
      throw new IllegalStateException("getToken: token missing/blank");
    return (String) tokenObj;
  }

  private static long decodeJwtExp(String jwt) {
    try {
      String[] parts = jwt.split("\\.");
      if (parts.length < 2) return Instant.now().plusSeconds(600).getEpochSecond();
      byte[] json = Base64.getUrlDecoder().decode(parts[1].getBytes(StandardCharsets.UTF_8));
      String payload = new String(json, StandardCharsets.UTF_8);

      int i = payload.indexOf("\"exp\":");
      if (i < 0) return Instant.now().plusSeconds(600).getEpochSecond();
      int start = i + 6;
      int end = start;
      while (end < payload.length() && Character.isDigit(payload.charAt(end))) end++;
      return Long.parseLong(payload.substring(start, end));
    } catch (Exception e) {
      return Instant.now().plusSeconds(600).getEpochSecond();
    }
  }

  private static final class Cached {
    final String token;
    final long exp;

    Cached(String t, long e) {
      this.token = t;
      this.exp = e;
    }
  }
}
