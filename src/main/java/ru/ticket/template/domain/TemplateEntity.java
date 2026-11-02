package ru.ticket.template.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.ticket.dto.Slide;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "templates", indexes = {
        @Index(name = "idx_templates_user", columnList = "user_id"),
        @Index(name = "idx_templates_schema", columnList = "schema_id")
})
public class TemplateEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "schema_id", length = 36)
    private String schemaId;

    @Column(nullable = false, length = 200)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "slides_json", nullable = false, columnDefinition = "JSON")
    private List<Slide> slides = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bindings_json", nullable = false, columnDefinition = "JSON")
    private List<BindingDto> bindings = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected TemplateEntity() { }

    public TemplateEntity(String userId,
                          String schemaId,
                          String name,
                          List<Slide> slides,
                          List<BindingDto> bindings) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.schemaId = schemaId;
        this.name = name;
        this.slides = slides == null ? new ArrayList<>() : new ArrayList<>(slides);
        this.bindings = bindings == null ? new ArrayList<>() : new ArrayList<>(bindings);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getSchemaId() { return schemaId; }
    public String getName() { return name; }
    public List<Slide> getSlides() { return slides; }
    public List<BindingDto> getBindings() { return bindings; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void replace(String schemaId,
                        String name,
                        List<Slide> slides,
                        List<BindingDto> bindings) {
        this.schemaId = schemaId;
        this.name = name;
        this.slides = slides == null ? new ArrayList<>() : new ArrayList<>(slides);
        this.bindings = bindings == null ? new ArrayList<>() : new ArrayList<>(bindings);
        this.updatedAt = Instant.now();
    }

    public void rename(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }
}