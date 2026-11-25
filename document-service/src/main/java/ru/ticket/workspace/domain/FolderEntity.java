package ru.ticket.workspace.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "folders", indexes = {
        @Index(name = "idx_folders_user", columnList = "user_id"),
        @Index(name = "idx_folders_parent", columnList = "parent_id")
})
public class FolderEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "parent_id", length = 36)
    private String parentId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected FolderEntity() { }

    public FolderEntity(String userId, String parentId, String name) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.parentId = parentId;
        this.name = name;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getParentId() { return parentId; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void rename(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public void moveTo(String parentId) {
        this.parentId = parentId;
        this.updatedAt = Instant.now();
    }
}