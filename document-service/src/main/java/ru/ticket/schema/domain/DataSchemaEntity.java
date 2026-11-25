package ru.ticket.schema.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "data_schemas", indexes = {
        @Index(name = "idx_schemas_user", columnList = "user_id")
})
public class DataSchemaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 200)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fields_json", nullable = false, columnDefinition = "JSON")
    private List<FieldDef> fields;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected DataSchemaEntity() { }

    public DataSchemaEntity(String userId, String name, List<FieldDef> fields) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.name = name;
        this.fields = List.copyOf(fields);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getName() { return name; }
    public List<FieldDef> getFields() { return fields; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void rename(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public void replaceFields(List<FieldDef> fields) {
        this.fields = List.copyOf(fields);
        this.updatedAt = Instant.now();
    }
}