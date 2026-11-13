package ru.ticket.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class RouteRateLimitTest {
  @Test void oneMultiStopWalkDoesNotConsumeDocumentIssuanceTokens() throws Exception {
    var limiter=new RateLimitFilter(true,20,2);
    for(int i=0;i<12;i++)assertEquals(200,post(limiter,"/api/journeys/routes/road"));
    assertEquals(200,post(limiter,"/api/journeys"));assertEquals(200,post(limiter,"/api/journeys"));
    assertEquals(429,post(limiter,"/api/journeys"));
  }
  private int post(RateLimitFilter limiter,String path) throws Exception {
    var request=new MockHttpServletRequest("POST",path);var response=new MockHttpServletResponse();
    limiter.doFilter(request,response,(req,res)->{});return response.getStatus();
  }
}
