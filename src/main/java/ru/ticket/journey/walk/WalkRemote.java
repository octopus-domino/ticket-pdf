package ru.ticket.journey.walk;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/** Small operator-constructed JSON requests. Exception messages never contain URL query secrets. */
@Component
public class WalkRemote {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WalkRemote.class);
  private final ObjectMapper mapper;

  public WalkRemote(ObjectMapper mapper) { this.mapper = mapper; }

  public JsonNode get(String url) throws IOException { return json(url, null, 5000); }
  public JsonNode post(String url, String form) throws IOException { return json(url, form, 5000); }

  private JsonNode json(String url, String form, int timeout) throws IOException {
    var connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
    try {
      connection.setConnectTimeout(WalkBudget.timeout(2000));
      connection.setReadTimeout(WalkBudget.timeout(timeout));
      connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("User-Agent", "CoDoc/1.0 (https://codoc.site; themed walking routes)");
      connection.setRequestProperty("Accept", "application/json");
      if (form != null) {
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        byte[] data = form.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(data.length);
        try (var output = connection.getOutputStream()) { output.write(data); }
      }
      int status = connection.getResponseCode();
      if (status != 200) {
        // ФИКС: логируем реальный статус и тело ответа, чтобы видеть, почему Photon 4xx
        String errorBody = "";
        try (var errorStream = connection.getErrorStream()) {
          if (errorStream != null) {
            byte[] bytes = errorStream.readNBytes(500);
            errorBody = new String(bytes, StandardCharsets.UTF_8);
          }
        }
        String host = connection.getURL().getHost();
        log.warn("walk.http status={} host={} body={}", status, host,
                errorBody.isEmpty() ? "<empty>" : errorBody.substring(0, Math.min(200, errorBody.length())));
        throw new IOException("Walk source unavailable: HTTP " + status + " from " + host);
      }
      byte[] data;
      try (var input = connection.getInputStream()) { data = input.readNBytes(1_000_001); }
      if (data.length > 1_000_000) throw new IOException("Walk source response too large");
      var result = mapper.readTree(data);
      if (result == null || result.has("error")) throw new IOException("Invalid walk source response");
      return result;
    } catch (IllegalArgumentException invalid) {
      throw new IOException("Invalid walk source response");
    } finally {
      connection.disconnect();
    }
  }

  public static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}