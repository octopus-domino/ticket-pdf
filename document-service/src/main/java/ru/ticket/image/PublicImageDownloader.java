package ru.ticket.image;

import java.io.IOException;
import java.net.*;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

/**
 * The checked DNS addresses are the addresses used by the connection manager. Redirects are
 * disabled.
 */
@Component
public class PublicImageDownloader {
  private static final int LIMIT = 10 * 1024 * 1024;

  public record Image(byte[] bytes, String mime) {}

  public Image download(String url) throws IOException {
    URI uri = URI.create(url);
    if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getUserInfo() != null)
      throw new IOException("Unsupported image URL");
    DnsResolver resolver =
        new DnsResolver() {
          public InetAddress[] resolve(String host) throws UnknownHostException {
            var addresses = InetAddress.getAllByName(host);
            for (var address : addresses)
              if (!isPublic(address))
                throw new UnknownHostException("Private image address blocked");
            return addresses;
          }

          public String resolveCanonicalHostname(String host) throws UnknownHostException {
            resolve(host);
            return host;
          }
        };
    var manager =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setDnsResolver(resolver)
            .setDefaultConnectionConfig(
                org.apache.hc.client5.http.config.ConnectionConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(10))
                    .setSocketTimeout(Timeout.ofSeconds(15))
                    .build())
            .build();
    var config =
        RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(10))
            .setResponseTimeout(Timeout.ofSeconds(15))
            .build();
    try (var client =
        HttpClients.custom()
            .setConnectionManager(manager)
            .setDefaultRequestConfig(config)
            .disableRedirectHandling()
            .build()) {
      return client.execute(
          new HttpGet(uri),
          response -> {
            if (response.getCode() < 200
                || response.getCode() >= 300
                || response.getEntity() == null) throw new IOException("Image HTTP error");
            if (response.getEntity().getContentLength() > LIMIT)
              throw new IOException("Image exceeds 10 MB");
            String mime = response.getEntity().getContentType();
            mime = mime == null ? "image/png" : mime.split(";")[0].trim();
            if (!java.util.Set.of("image/png", "image/jpeg", "image/gif", "image/webp")
                .contains(mime)) throw new IOException("Raster image required");
            try (var stream = response.getEntity().getContent()) {
              byte[] bytes = stream.readNBytes(LIMIT + 1);
              if (bytes.length > LIMIT) throw new IOException("Image exceeds 10 MB");
              return new Image(bytes, mime);
            }
          });
    }
  }

  public static boolean isPublic(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) return false;
    byte[] bytes = address.getAddress();
    int first = bytes[0] & 255;
    return bytes.length == 16
        ? (first & 254) != 252
        : first != 0
            && first < 224
            && !(first == 100 && (bytes[1] & 255) >= 64 && (bytes[1] & 255) <= 127);
  }
}
