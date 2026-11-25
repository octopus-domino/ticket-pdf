package ru.ticket.journey.infrastructure.media;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "journey_media")
public class JourneyMedia {
  @Id
  @Column(length = 64)
  String id;

  @Column(nullable = false, length = 900000)
  byte[] content;

  @Column(nullable = false, length = 32)
  String contentType;

  int width;
  int height;
  Instant createdAt;

  public byte[] content() {
    return content;
  }

  public String contentType() {
    return contentType;
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public String dataUri() {
    return "data:" + contentType + ";base64," + java.util.Base64.getEncoder().encodeToString(content);
  }
}
