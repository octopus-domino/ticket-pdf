package ru.ticket.walks.infrastructure.geocoding;

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

  @Test
  void findsTheCityForAPointAndCachesTheLocalName() throws Exception {
    var calls = new AtomicInteger();
    var query = new java.util.concurrent.atomic.AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/photon/reverse", exchange -> {
      calls.incrementAndGet();
      query.set(exchange.getRequestURI().getQuery());
      byte[] body = """
          {"features":[
            {"properties":{"name":"Palace Square","type":"locality","osm_key":"place","osm_value":"square"}},
            {"properties":{"name":"Building","type":"locality","city":"Санкт-Петербург"}}
          ]}
          """.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      try (var out = exchange.getResponseBody()) { out.write(body); }
    });
    server.start();
    try {
      var service = new AddressSearchService(new ObjectMapper(),
          "http://127.0.0.1:" + server.getAddress().getPort() + "/photon/api/");
      assertThat(service.cityAt(30.3146, 59.9398, "ru").city()).isEqualTo("Санкт-Петербург");
      assertThat(service.cityAt(30.31460001, 59.9398, "ru").city()).isEqualTo("Санкт-Петербург");
      assertThat(query.get()).contains("lat=59.93980", "lon=30.31460", "layer=city").doesNotContain("lang=");
      assertThatThrownBy(() -> service.cityAt(Double.NaN, 55, "en")).hasMessage("journey.route.invalid-route");
      assertThat(calls.get()).isOne();
    } finally {
      server.stop(0);
    }
  }
}
