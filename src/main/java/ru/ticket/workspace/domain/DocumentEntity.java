package ru.ticket.workspace.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents", indexes = {
        @Index(name = "idx_docs_user", columnList = "user_id"),
        @Index(name = "idx_docs_folder", columnList = "folder_id")
})
public class DocumentEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "folder_id", length = 36)
    private String folderId;

    @Column(nullable = false, length = 200)
    private String name;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String content;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected DocumentEntity() { }

    public DocumentEntity(String userId, String folderId, String name, String content) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.folderId = folderId;
        this.name = name;
        this.content = content;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getFolderId() { return folderId; }
    public String getName() { return name; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void rename(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public void moveTo(String folderId) {
        this.folderId = folderId;
        this.updatedAt = Instant.now();
    }

    public void saveContent(String content) {
        this.content = content;
        this.updatedAt = Instant.now();
    }
}