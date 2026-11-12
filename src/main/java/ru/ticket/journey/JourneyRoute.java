package ru.ticket.journey;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "journey_routes")
public class JourneyRoute {
  @Id
  @Column(length = 64)
  String id;

  @Column(nullable = false, length = 750000)
  String payload;

  Instant createdAt;
}
