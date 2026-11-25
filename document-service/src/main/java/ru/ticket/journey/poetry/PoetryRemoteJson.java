package ru.ticket.journey.poetry;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.net.*;
import org.springframework.stereotype.Component;

/** Only application-constructed provider URLs reach this adapter; no user-supplied fetch URLs. */
@Component
public class PoetryRemoteJson {
  private final ObjectMapper mapper;
  public PoetryRemoteJson(ObjectMapper mapper) { this.mapper = mapper; }
  public JsonNode get(String url, int maxBytes) throws IOException {
    return get(url, maxBytes, 10000);
  }
  public JsonNode get(String url, int maxBytes, int readTimeout) throws IOException {
    var connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
    try {
      connection.setConnectTimeout(PoetryFetchBudget.timeout(4000)); connection.setReadTimeout(PoetryFetchBudget.timeout(readTimeout)); connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("User-Agent", "CoDoc/1.0 (https://codoc.site; weather-aware poetry cards)");
      connection.setRequestProperty("Accept", "application/json");
      if (connection.getResponseCode() != 200) throw new IOException("Poetry provider " + connection.getURL().getHost() + " returned " + connection.getResponseCode());
      byte[] body;
      try (var input = connection.getInputStream()) { body = input.readNBytes(maxBytes + 1); }
      if (body.length > maxBytes) throw new IOException("Poetry provider response is too large");
      return mapper.readTree(body);
    } finally { connection.disconnect(); }
  }
}
