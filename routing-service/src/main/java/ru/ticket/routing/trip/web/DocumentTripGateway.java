package ru.ticket.routing.trip.web;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import ru.ticket.routing.trip.TripAccountGateway;

/** Reuses the account service's access-token validation without a copy of its signing key. */
@Component
class DocumentTripGateway implements TripAccountGateway {
  private final RestClient documents;

  DocumentTripGateway(RestClient.Builder client, @Value("${TRIPS_DOCUMENT_URL:http://127.0.0.1:8080}") String url) {
    var requests = new SimpleClientHttpRequestFactory();
    requests.setConnectTimeout(Duration.ofSeconds(2));
    requests.setReadTimeout(Duration.ofSeconds(6));
    documents = client.baseUrl(url).requestFactory(requests).build();
  }

  @Override
  public String owner(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in");
    }
    JsonNode response;
    try {
      response = get(authorization, "/api/auth/me");
    } catch (ResponseStatusException error) {
      if (error.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in");
      }
      throw error;
    }
    String owner = response.path("id").asText("");
    if (owner.isBlank() || owner.length() > 36) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid account response");
    }
    return owner;
  }

  @Override
  public JsonNode legacyTrip(String authorization, String id) {
    return get(authorization, "/api/trips/legacy/{id}", id);
  }

  private JsonNode get(String authorization, String path, Object... parameters) {
    try {
      var body = documents.get().uri(path, parameters).header(HttpHeaders.AUTHORIZATION, authorization)
          .retrieve().body(JsonNode.class);
      if (body != null && body.isObject()) return body;
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid account service response");
    } catch (RestClientResponseException error) {
      switch (error.getStatusCode().value()) {
        case 401 -> throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in");
        case 403, 404 -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        default -> throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Account service unavailable");
      }
    } catch (RestClientException error) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Account service unavailable");
    }
  }
}
