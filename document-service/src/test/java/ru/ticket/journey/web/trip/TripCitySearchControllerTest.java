package ru.ticket.journey.web.trip;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import ru.ticket.journey.domain.service.CitySearch;
import ru.ticket.journey.infrastructure.geocoding.NominatimCitySearch;

class TripCitySearchControllerTest {
  @Test
  void returnsOnlySettlementsAndCachesTheProviderResult() throws Exception {
    var calls = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/search", exchange -> {
      calls.incrementAndGet();
      byte[] bytes = """
          [{"name":"Тверь","display_name":"Тверь, Россия","addresstype":"city","lat":"56.85","lon":"35.91"},
           {"name":"Музей","display_name":"Музей, Тверь","addresstype":"museum","lat":"56.85","lon":"35.91"}]
          """.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length);
      try (var response = exchange.getResponseBody()) { response.write(bytes); }
    });
    server.start();
    try {
      var controller = new TripCitySearchController(new NominatimCitySearch(new ObjectMapper(),
          "http://127.0.0.1:" + server.getAddress().getPort() + "/search"));
      var results = controller.search("Тверь", "ru");
      assertThat(results).containsExactly(new CitySearch.City("Тверь", "Тверь, Россия", 56.85, 35.91));
      assertThat(controller.search(" тверь ", "ru")).isEqualTo(results);
      assertThat(calls).hasValue(1);
    } finally {
      server.stop(0);
    }
  }
}
