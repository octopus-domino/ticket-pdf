package ru.ticket.email;

import jakarta.persistence.*;
import java.time.Instant;

/** A durable reservation avoids silently resending recipients after a partial SMTP failure. */
@Entity
@Table(name = "email_deliveries")
public class DeliveryReceipt {
  @Id
  @Column(length = 64)
  private String id;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(nullable = false)
  private Instant createdAt;

  protected DeliveryReceipt() {}

  public DeliveryReceipt(String id) {
    this.id = id;
    status = "PENDING";
    createdAt = Instant.now();
  }

  public String getStatus() {
    return status;
  }

  public void sent() {
    status = "SENT";
  }

  public void uncertain() {
    status = "UNKNOWN";
  }
}
