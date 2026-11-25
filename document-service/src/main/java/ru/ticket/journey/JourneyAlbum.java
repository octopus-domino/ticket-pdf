package ru.ticket.journey;

import jakarta.persistence.*;

@Entity
@Table(name = "journey_albums")
public class JourneyAlbum {
  @Id @Column(length = 36) public String id;
  @Column(nullable = false, columnDefinition = "TEXT") public String pictures = "[]";
  @Column(nullable = false, length = 64) public String invitationHash;
  @Column(nullable = false, columnDefinition = "TEXT") public String invitationCipher;
  @Version public Long version;
}
