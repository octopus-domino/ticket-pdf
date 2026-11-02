package ru.ticket.schema.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;
import ru.ticket.datamodel.repository.DataModelRepository;
import ru.ticket.schema.domain.FieldDef;
import ru.ticket.schema.domain.SchemaEntity;
import ru.ticket.schema.dto.SchemaDtos.*;
import ru.ticket.schema.repository.SchemaRepository;

import java.util.List;

@Service
public class SchemaServiceImpl implements SchemaService {

    private final SchemaRepository schemas;
    private final DataModelRepository models;

    public SchemaServiceImpl(SchemaRepository schemas, DataModelRepository models) {
        this.schemas = schemas;
        this.models = models;
    }

    @Override
    public List<SchemaDto> list(String userId) {
        return schemas.findAllByUserIdOrderByUpdatedAtDesc(userId)
                .stream().map(this::toDto).toList();
    }

    @Override
    public SchemaDto get(String userId, String schemaId) {
        return toDto(find(userId, schemaId));
    }

    @Override
    @Transactional
    public SchemaDto create(String userId, CreateSchemaRequest req) {
        return toDto(schemas.save(new SchemaEntity(userId, req.name().trim(), toFields(req.fields()))));
    }

    @Override
    @Transactional
    public SchemaDto update(String userId, String schemaId, UpdateSchemaRequest req) {
        SchemaEntity s = find(userId, schemaId);
        s.rename(req.name().trim());
        s.replaceFields(toFields(req.fields()));
        return toDto(schemas.save(s));
    }

    @Override
    @Transactional
    public void delete(String userId, String schemaId) {
        SchemaEntity s = find(userId, schemaId);
        long inUse = models.countBySchemaId(schemaId);
        if (inUse > 0) throw ApiException.conflict("Schema in use by " + inUse + " model(s)");
        schemas.delete(s);
    }

    private SchemaEntity find(String userId, String id) {
        return schemas.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Schema"));
    }

    private List<FieldDef> toFields(List<FieldDto> dtos) {
        return dtos.stream()
                .map(d -> new FieldDef(d.path(), d.type(), d.required(), d.label(), d.defaultValue()))
                .toList();
    }

    private SchemaDto toDto(SchemaEntity s) {
        List<FieldDto> fields = s.getFields().stream()
                .map(f -> new FieldDto(f.path(), f.type(), f.required(), f.label(), f.defaultValue()))
                .toList();
        return new SchemaDto(s.getId(), s.getName(), fields, s.getCreatedAt(), s.getUpdatedAt());
    }
}