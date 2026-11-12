package ru.ticket.journey;

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
}
