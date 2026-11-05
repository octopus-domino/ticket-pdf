package ru.ticket.connectors;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.*;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;

@Component
public class SourceHttpClient {
  private final Set<String> origins;
  private final ObjectMapper mapper;
  public SourceHttpClient(ObjectMapper mapper, @Value("${app.connectors.allowed-origins:}") String origins) {
    this.mapper=mapper;
    this.origins=new HashSet<>();
    for (String value : origins.split(",")) if (!value.isBlank()) this.origins.add(origin(URI.create(value.trim())));
  }
  public String validate(String url) {
    try {
      URI uri = URI.create(url);
      if (url.length()>4000 || !Set.of("https", "http").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null
          || !origins.contains(origin(uri))) throw ApiException.badRequest("Source origin is not allowed by the administrator");
      return origin(uri);
    } catch (IllegalArgumentException error) { throw ApiException.badRequest("Invalid source URL"); }
  }
  private static String origin(URI uri) {
    if (uri.getHost()==null) throw new IllegalArgumentException("Absolute source origin required");
    int port = uri.getPort();
    return uri.getScheme().toLowerCase(Locale.ROOT)+"://"+uri.getHost().toLowerCase(Locale.ROOT)+(port<0 || port==80&&uri.getScheme().equals("http") || port==443&&uri.getScheme().equals("https") ? "" : ":"+port);
  }
  public JsonNode fetch(String url, String authorization) {
    validate(url);
    var manager=PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(new DnsResolver() {
      public InetAddress[] resolve(String host) throws UnknownHostException {
        var addresses=InetAddress.getAllByName(host);
        for (var address:addresses) if (address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isMulticastAddress()) throw new UnknownHostException("Invalid source address");
        return addresses;
      }
      public String resolveCanonicalHostname(String host) throws UnknownHostException { resolve(host); return host; }
    }).setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(5)).setSocketTimeout(Timeout.ofSeconds(20)).build()).build();
    try (var client=HttpClients.custom().setConnectionManager(manager).disableRedirectHandling()
        .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(20)).build()).build()) {
      var request=new HttpGet(URI.create(url)); request.setHeader("Accept", "application/json");
      if (!authorization.isBlank()) request.setHeader("Authorization", authorization);
      return client.execute(request, response -> {
        if (response.getCode()!=200 || response.getEntity()==null) throw new IOException("Source request rejected");
        if (response.getEntity().getContentLength()>8_000_000) throw new IOException("Source exceeds 8 MB");
        try (var stream=response.getEntity().getContent()) {
          byte[] bytes=stream.readNBytes(8_000_001);
          if (bytes.length>8_000_000) throw new IOException("Source exceeds 8 MB");
          return mapper.readTree(bytes);
        }
      });
    } catch (IOException error) { throw ApiException.badRequest("Source request failed. Check the connection, credentials and JSON endpoint."); }
  }
}
