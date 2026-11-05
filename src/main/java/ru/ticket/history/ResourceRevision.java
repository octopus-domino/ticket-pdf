package ru.ticket.history;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="resource_revisions", indexes={@Index(name="idx_revision_owner",columnList="user_id"),@Index(name="idx_revision_resource",columnList="resource_id")})
public class ResourceRevision {
  @Id public String id=UUID.randomUUID().toString();
  @Column(name="user_id",nullable=false) public String userId;
  @Column(nullable=false,length=16) public String kind;
  @Column(name="resource_id",nullable=false) public String resourceId;
  @Column(name="content_hash",nullable=false,length=64) public String contentHash;
  @Column(name="created_at",nullable=false) public Instant createdAt=Instant.now();
  @Lob @Column(nullable=false) public String payload;
  @Column(name="payload_bytes",nullable=false) public int payloadBytes;
}
