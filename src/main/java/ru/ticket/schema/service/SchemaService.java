package ru.ticket.schema.service;

import ru.ticket.schema.dto.SchemaDtos.CreateSchemaRequest;
import ru.ticket.schema.dto.SchemaDtos.SchemaDto;
import ru.ticket.schema.dto.SchemaDtos.UpdateSchemaRequest;

import java.util.List;

public interface SchemaService {
    List<SchemaDto> list(String userId);
    SchemaDto get(String userId, String schemaId);
    SchemaDto create(String userId, CreateSchemaRequest req);
    SchemaDto update(String userId, String schemaId, UpdateSchemaRequest req);
    void delete(String userId, String schemaId);
}