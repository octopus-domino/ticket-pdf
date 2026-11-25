package ru.ticket.workflow;

import jakarta.persistence.*;

@Entity
@Table(name = "site_addresses")
public class SiteAddress {
  @Id
  @Column(length = 48)
  public String slug;

  @Column(nullable = false, length = 16)
  public String kind;

  @Version public Long version;
}
