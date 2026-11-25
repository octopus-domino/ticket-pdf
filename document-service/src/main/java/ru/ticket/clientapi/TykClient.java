package ru.ticket.clientapi;


import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;


@Component
public class TykClient {
  private final String url, secret;
  private final ObjectMapper mapper;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();

  public TykClient(ObjectMapper mapper, @Value("${app.client-api.tyk-url:}") String url, @Value("${app.client-api.tyk-secret:}") String secret) {
    this.mapper = mapper; this.url = url; this.secret = secret;
  }

  public boolean enabled() { return !url.isBlank() && secret.length() >= 32; }

  public void create(String token, String userId, Instant expires) {
    Map<String, Object> session = Map.of("org_id", "codoc", "alias", "codoc-client",
        "rate", 60, "per", 60, "quota_max", 10000, "quota_renewal_rate", 2592000,
        "expires", expires.getEpochSecond(), "access_rights", Map.of("codoc-client", Map.of("api_id", "codoc-client", "api_name", "CoDoc client API", "versions", List.of("Default"))),
        "meta_data", Map.of("owner", userId));
    request("POST", token, session);
  }

  public void revoke(String token) { request("DELETE", token, null); }

  private void request(String method, String token, Object body) {
    if (!enabled()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Client API gateway is not configured");
    }
    try {
      var request = HttpRequest.newBuilder(URI.create(url).resolve("/tyk/keys/" + token)).timeout(Duration.ofSeconds(8))
          .header("x-tyk-authorization", secret).header("Content-Type", "application/json")
          .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body))).build();
      var response = client.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() / 100 != 2 && !(method.equals("DELETE") && response.statusCode() == 404))
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Client API gateway is unavailable");
    } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Gateway request interrupted"); }
    catch (java.io.IOException error) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Client API gateway is unavailable"); }
  }
}
