package ru.ticket.walks.infrastructure.media;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.ticket.walks.application.PrintMedia;
import ru.ticket.walks.shared.ApiException;

@Component
public class HttpPrintMedia implements PrintMedia {
    public record Asset(String id) {}

    private final RestClient documents;

    public HttpPrintMedia(@Value("${walks.print-media-url}") String origin) {
        var timeouts = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).version(HttpClient.Version.HTTP_1_1).build());
        timeouts.setReadTimeout(Duration.ofSeconds(4));
        documents = RestClient.builder().baseUrl(origin).requestFactory(timeouts).build();
    }

    @Override
    public String save(String imageData) {
        try {
            var result = documents.post().uri("/api/journeys/media")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(Map.of("image", imageData)).retrieve().body(Asset.class);
            if (result != null && result.id() != null && result.id().matches("[a-f0-9]{64}")) return result.id();
        } catch (RestClientException unavailable) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.media.unavailable");
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.media.unavailable");
    }
}
