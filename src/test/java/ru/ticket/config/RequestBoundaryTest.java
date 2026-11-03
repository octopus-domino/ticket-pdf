package ru.ticket.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class RequestBoundaryTest {
  @Test
  void pollingDoesNotConsumeLaunchBudget() throws Exception {
    var filter = new RateLimitFilter(true, 20, 10);
    for (int i = 0; i < 100; i++) {
      var response = new MockHttpServletResponse();
      filter.doFilter(
          new MockHttpServletRequest("GET", "/api/generate/job"), response, (a, b) -> {});
      assertEquals(200, response.getStatus());
    }
    int status = 0;
    for (int i = 0; i < 11; i++) {
      var response = new MockHttpServletResponse();
      filter.doFilter(new MockHttpServletRequest("POST", "/api/generate"), response, (a, b) -> {});
      status = response.getStatus();
    }
    assertEquals(429, status);
  }

  @Test
  void chunkedJsonBodyCannotBypassTheSizeLimit() throws Exception {
    var request = new MockHttpServletRequest("POST", "/api/data-models/import");
    request.setContentType("application/json");
    request.setContent(new byte[20 * 1024 * 1024 + 1]);
    var response = new MockHttpServletResponse();
    new JsonBodyLimitFilter()
        .doFilter(request, response, (a, b) -> fail("Oversized request reached controller"));
    assertEquals(413, response.getStatus());
  }
}
