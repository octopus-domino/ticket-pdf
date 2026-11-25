package ru.ticket.journey.infrastructure;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.ticket.common.ApiException;
import ru.ticket.journey.domain.model.Trip;

/** Fetches the account copy from routing before rendering an export. */
@Component
@ConditionalOnProperty(name = "app.journeys.trip-planner-enabled", havingValue = "true")
public class RoutingTripClient {
  private final RestClient routing;

  public RoutingTripClient(RestClient.Builder client,
      @Value("${TRIP_ROUTING_URL:http://127.0.0.1:8093}") String url) {
    var requests = new SimpleClientHttpRequestFactory();
    requests.setConnectTimeout(Duration.ofSeconds(2));
    requests.setReadTimeout(Duration.ofSeconds(10));
    routing = client.baseUrl(url).requestFactory(requests).build();
  }

  public Trip read(String id, String auth) {
    try {
      var trip = routing.get().uri("/api/routing/v1/trips/{id}", id)
          .header(HttpHeaders.AUTHORIZATION, auth).retrieve().body(Trip.class);
      if (trip == null) throw new ApiException(HttpStatus.BAD_GATEWAY, "Invalid routing service response");
      return trip;
    } catch (RestClientResponseException error) {
      switch (error.getStatusCode().value()) {
        case 400 -> throw ApiException.badRequest("journey.trip.invalid-id");
        case 401 -> throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in");
        case 403, 404 -> throw ApiException.notFound("Trip");
        default -> throw new ApiException(HttpStatus.BAD_GATEWAY, "Routing service unavailable");
      }
    } catch (ResourceAccessException error) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Routing service unavailable");
    } catch (RestClientException error) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "Invalid routing service response");
    }
  }
}
