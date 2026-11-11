package ru.ticket.auth.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;
import ru.ticket.config.RateLimitFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtFilter;
  private final RateLimitFilter rateLimitFilter;
  private final CorsConfigurationSource corsConfigurationSource;
  private final OAuth2SuccessHandler oAuth2SuccessHandler;
  private final ru.ticket.clientapi.ClientApiKeyFilter clientKeyFilter;

  public SecurityConfig(
      JwtAuthenticationFilter jwtFilter,
      RateLimitFilter rateLimitFilter,
      CorsConfigurationSource corsConfigurationSource,
      OAuth2SuccessHandler oAuth2SuccessHandler,
      ru.ticket.clientapi.ClientApiKeyFilter clientKeyFilter) {
    this.jwtFilter = jwtFilter;
    this.rateLimitFilter = rateLimitFilter;
    this.corsConfigurationSource = corsConfigurationSource;
    this.oAuth2SuccessHandler = oAuth2SuccessHandler;
    this.clientKeyFilter = clientKeyFilter;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.cors(c -> c.configurationSource(corsConfigurationSource))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/api/qr",
                        "/api/journeys/**",
                        "/api/journey-sites/**",
                        "/api/public/workflows/**",
                        "/api/links/**",
                        "/api/guest/export/svg",
                        "/api/guest/export/pdf",
                        "/api/guest/recognition",
                        "/api/guest/recognition/capabilities",
                        "/api/guest/generate/sync",
                        "/api/guest/generate/preview",
                        "/oauth2/**",
                        "/login/oauth2/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.defaultAuthenticationEntryPointFor(
                    (request, response, authException) -> {
                      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                      response.getWriter().write("{\"error\":\"Unauthorized\"}");
                    },
                    new AntPathRequestMatcher("/api/**")))
        .oauth2Login(oauth2 -> oauth2.successHandler(oAuth2SuccessHandler))
        .headers(h -> h.frameOptions(f -> f.sameOrigin()))
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(clientKeyFilter, JwtAuthenticationFilter.class)
        // rate-limit идёт ПОСЛЕ аутентификации: bucket-ключ = userId,
        // а не IP за общим NAT.
        .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public org.springframework.boot.web.servlet.FilterRegistrationBean<ru.ticket.clientapi.ClientApiKeyFilter> clientKeyFilterRegistration() {
    var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(clientKeyFilter);
    registration.setEnabled(false); return registration;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }
}
