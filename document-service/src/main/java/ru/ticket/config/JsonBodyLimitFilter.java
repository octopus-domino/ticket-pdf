package ru.ticket.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bound JSON bodies, including chunked requests, before Jackson allocates the model graph. */
@Component
public class JsonBodyLimitFilter extends OncePerRequestFilter {
  private static final int LIMIT = 20 * 1024 * 1024;

  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentType() == null
        || !request.getContentType().toLowerCase().contains("application/json")) {
      chain.doFilter(request, response);
      return;
    }
    int limit =
        request.getRequestURI().equals("/api/journeys/media")
            ? 1250000
            : request.getRequestURI().startsWith("/api/journeys/routes") ? 800000 : LIMIT;
    byte[] body = request.getInputStream().readNBytes(limit + 1);
    if (body.length > limit) {
      response.setStatus(413);
      response.setContentType("application/json");
      response
          .getWriter()
          .write(
              "{\"error\":\""
                  + (limit == LIMIT ? "JSON body exceeds 20 MB" : "journey.media.too-large")
                  + "\",\"status\":413}");
      return;
    }
    chain.doFilter(
        new HttpServletRequestWrapper(request) {
          public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
              public int read() {
                return input.read();
              }

              public int read(byte[] bytes, int offset, int length) {
                return input.read(bytes, offset, length);
              }

              public boolean isFinished() {
                return input.available() == 0;
              }

              public boolean isReady() {
                return true;
              }

              public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException();
              }
            };
          }

          public BufferedReader getReader() {
            return new BufferedReader(
                new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
          }
        },
        response);
  }
}
