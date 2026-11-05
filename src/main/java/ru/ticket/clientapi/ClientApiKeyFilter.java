package ru.ticket.clientapi;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.common.ApiException;

@Component
public class ClientApiKeyFilter extends OncePerRequestFilter {
  private final ClientApiKeyService service;
  public ClientApiKeyFilter(ClientApiKeyService service) { this.service = service; }
  @Override protected boolean shouldNotFilter(HttpServletRequest request) { return request.getHeader("X-API-Key") == null || request.getMethod().equals("OPTIONS"); }
  @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
    try {
      if (request.getHeader("Authorization") != null) throw ApiException.badRequest("Use either X-API-Key or Authorization");
      String owner = service.authenticate(request.getHeader("X-API-Key"), request.getMethod(), request.getRequestURI());
      SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new UserIdPrincipal(owner), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    } catch (ApiException error) {
      response.setStatus(error.status().value()); response.setContentType("application/json");
      response.getWriter().write("{\"error\":\"Client API authentication, scope or quota rejected the request\"}");
      return;
    }
    chain.doFilter(request, response);
  }
}
