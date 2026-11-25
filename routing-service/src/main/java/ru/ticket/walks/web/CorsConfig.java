package ru.ticket.walks.web;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

  @Bean
  public CorsFilter corsFilter() {
    CorsConfiguration config = new CorsConfiguration();

    config.setAllowedOriginPatterns(
            List.of(
                    // Локальная разработка
                    "http://localhost:5173",
                    "http://localhost:5174",
                    "http://localhost:3000",
                    "http://127.0.0.1:5173",

                    // Capacitor / Android / iOS WebView.
                    // Android с androidScheme: 'https' → origin "https://localhost".
                    // iOS (на будущее) → "capacitor://localhost".
                    // http://localhost — на случай сборки без androidScheme: 'https'.
                    "https://localhost",
                    "capacitor://localhost",
                    "http://localhost",

                    // Продакшн-домены
                    "https://www.codoc.site",
                    "https://codoc.site",

                    // Legacy — оставлено на переходный период.
                    "https://213.171.29.210.sslip.io"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    config.setAllowedHeaders(List.of("*"));
    config.setExposedHeaders(List.of("Retry-After"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return new CorsFilter(source);
  }
}