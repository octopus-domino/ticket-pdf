package ru.ticket.datamodel.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "data_models", indexes = {
        @Index(name = "idx_model_user", columnList = "user_id"),
        @Index(name = "idx_model_schema", columnList = "schema_id")
})
public class DataModelEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "schema_id", nullable = false, length = 36)
    private String schemaId;

    @Column(nullable = false, length = 200)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "values_json", nullable = false, columnDefinition = "JSON")
    private Map<String, Object> values;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected DataModelEntity() { }

    public DataModelEntity(String userId, String schemaId, String name, Map<String, Object> values) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.schemaId = schemaId;
        this.name = name;
        this.values = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getSchemaId() { return schemaId; }
    public String getName() { return name; }
    public Map<String, Object> getValues() { return values; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void rename(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public void replaceValues(Map<String, Object> values) {
        this.values = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
        this.updatedAt = Instant.now();
    }
}