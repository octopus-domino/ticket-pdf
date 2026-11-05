package ru.ticket.clientapi;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="api_client_keys", indexes=@Index(name="idx_api_keys_owner", columnList="user_id"))
public class ClientApiKey {
  @Id public String id = UUID.randomUUID().toString();
  @Column(name="user_id", nullable=false) public String userId;
  @Column(nullable=false, length=100) public String name;
  @Column(name="token_hash", nullable=false, unique=true, length=64) public String tokenHash;
  @Column(nullable=false, length=20) public String prefix;
  @Column(name="encrypted_token", nullable=false, length=1000) public String encryptedToken;
  @Column(nullable=false, length=100) public String scopes;
  @Column(name="created_at", nullable=false) public Instant createdAt = Instant.now();
  @Column(name="expires_at", nullable=false) public Instant expiresAt;
  public boolean revoked;
  @Column(name="minute_window") public long minuteWindow;
  @Column(name="minute_count") public int minuteCount;
  @Column(name="month_window", length=7) public String monthWindow;
  @Column(name="month_count") public int monthCount;
  @Column(name="last_used_at") public Instant lastUsedAt;
}
