package ru.ticket.journey.poetry;

import jakarta.persistence.*;
import java.time.Instant;

/** Selected user output, not a bundled anthology. The snapshot makes later downloads reproducible. */
@Entity
@Table(name = "poetry_sets")
public class PoetrySet {
  @Id @Column(length = 64) public String id;
  @Column(nullable = false, columnDefinition = "TEXT") public String payload;
  @Column(nullable = false) public Instant createdAt;
}
