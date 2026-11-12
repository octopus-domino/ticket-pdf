package ru.ticket.journey;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AddressSearchTest {
  @Test
  void normalizesCachesAndBoundsProviderTrafficWithoutAnAccount() throws Exception {
    var calls = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/",
        exchange -> {
          calls.incrementAndGet();
          byte[] body =
              """
{"features":[{"geometry":{"coordinates":[13.388,52.517]},"properties":{"name":"Museum","street":"Main Street","housenumber":"1","city":"Berlin","country":"Germany"}},
{"geometry":{"coordinates":[999,999]},"properties":{"name":"Invalid"}}]}
"""
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (var out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    server.start();
    try {
      var service =
          new AddressSearchService(
              new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort() + "/api/");
      var found = service.search(" Museum   Berlin ", "de");
      assertThat(found).hasSize(1);
      assertThat(found.get(0).label()).isEqualTo("Museum, Main Street 1, Berlin, Germany");
      assertThat(service.search("museum berlin", "de")).isEqualTo(found);
      assertThat(calls.get()).isEqualTo(1);
      assertThat(service.search("ab", "de")).isEmpty();
      assertThatThrownBy(() -> service.search("Other place", "de"))
          .hasMessage("journey.route.search-busy");
      assertThat(calls.get()).isEqualTo(1);
    } finally {
      server.stop(0);
    }
  }
}
