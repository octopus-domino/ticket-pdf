package ru.ticket.journey;

import jakarta.persistence.*;
import java.time.Instant;

/** Frozen native template/schema/model source; editing an account template never silently changes a published PDF. */
@Entity
@Table(name = "journey_print_sources")
public class JourneyPrintSource {
  @Id @Column(length = 64) public String id;
  @Column(nullable = false, columnDefinition = "TEXT") public String sourceJson;
  @Column(nullable = false) public Instant updatedAt;
  @Version public Long version;
}
