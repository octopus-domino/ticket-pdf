package ru.ticket.connectors;


import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


@Entity @Table(name="source_connections", indexes=@Index(name="idx_source_owner", columnList="user_id"))
public class SourceConnection {
  @Id public String id = UUID.randomUUID().toString();
  @Column(name="user_id", nullable=false) public String userId;
  @Column(nullable=false, length=200) public String name;
  @Column(nullable=false, length=30) public String provider;
  @Column(nullable=false, length=300) public String origin;
  @Column(name="encrypted_url", nullable=false, length=6000) public String encryptedUrl;
  @Column(name="encrypted_auth", nullable=false, length=6000) public String encryptedAuth;
  @Column(name="schema_id") public String schemaId;
  @Column(name="key_path", length=1000) public String keyPath;
  @Column(name="name_path", length=1000) public String namePath;
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="mapping_json", columnDefinition="JSON") public Map<String, String> mapping = new LinkedHashMap<>();
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="links_json", columnDefinition="JSON") public Map<String, String> links = new LinkedHashMap<>();
  @Column(name="auto_sync_minutes") public int autoSyncMinutes;
  @Column(name="last_sync_at") public Instant lastSyncAt;
  @Column(name="last_status", length=20) public String lastStatus;
}
