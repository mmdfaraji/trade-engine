package com.arbitrage.config;

import com.arbitrage.entities.Exchange;
import com.arbitrage.entities.ExchangeAccount;
import com.arbitrage.service.ExchangeAccessService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RamzinexTokenProvider {

  public static final String PATH_GET_TOKEN = "/exchange/api/v1.0/exchange/auth/api_key/getToken";
  private final ExchangeAccessService directory;
  private final ExchangeClientFactory factory;
  private final Map<String, Cached> cache = new ConcurrentHashMap<>();

  public RamzinexTokenProvider(ExchangeAccessService directory, ExchangeClientFactory factory) {
    this.directory = directory;
    this.factory = factory;
  }

  public String token(String exchangeName, String accountLabel) {
    String key = (exchangeName + ":" + accountLabel).toLowerCase(Locale.ROOT);
    Cached e = cache.get(key);
    long now = Instant.now().getEpochSecond();
    if (e != null && e.exp - 30 > now) return e.token;

    synchronized (cache.computeIfAbsent(key, k -> new Cached(null, 0))) {
      e = cache.get(key);
      if (e != null && e.exp - 30 > now) return e.token;

      Exchange ex = directory.requireExchange(exchangeName);
      ExchangeAccount acc = directory.requireAccount(exchangeName, accountLabel);
      RestClient openRestClient = factory.buildPublic(ex.getPrivateApiUrl());

      Map<?, ?> response =
          openRestClient
              .post()
              .uri(PATH_GET_TOKEN)
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("api_key", acc.getApiKey(), "secret", acc.getSecretKey()))
              .retrieve()
              .body(Map.class);

      String token = extractToken(response);
      long exp = decodeExp(token);
      cache.put(key, new Cached(token, exp));
      return token;
    }
  }

  private static String extractToken(Map<?, ?> resp) {
    if (resp == null) throw new IllegalStateException("getToken: response is null");
    Object data = resp.get("data");
    if (!(data instanceof Map<?, ?>)) throw new IllegalStateException("getToken: missing data");
    Object t = ((Map) data).get("token");
    if (!(t instanceof String) || t.toString().isBlank())
      throw new IllegalStateException("getToken: token missing");
    return (String) t;
  }

  private static long decodeExp(String jwt) {
    try {
      String[] parts = jwt.split("\\.");
      if (parts.length < 2) return Instant.now().plusSeconds(600).getEpochSecond();
      byte[] payload = Base64.getUrlDecoder().decode(parts[1].getBytes(StandardCharsets.UTF_8));
      String json = new String(payload, StandardCharsets.UTF_8);
      int i = json.indexOf("\"exp\":");
      if (i < 0) return Instant.now().plusSeconds(600).getEpochSecond();
      int s = i + 6, e = s;
      while (e < json.length() && Character.isDigit(json.charAt(e))) e++;
      return Long.parseLong(json.substring(s, e));
    } catch (Exception ex) {
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
