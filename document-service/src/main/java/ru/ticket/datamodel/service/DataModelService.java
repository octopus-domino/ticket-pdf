package ru.ticket.datamodel.service;

import ru.ticket.datamodel.dto.DataModelDtos.*;

import java.util.List;

public interface DataModelService {
    List<ModelDto> list(String userId, String schemaId);
    ModelDto get(String userId, String modelId);
    ModelDto create(String userId, CreateModelRequest req);
    ModelDto update(String userId, String modelId, UpdateModelRequest req);
    void delete(String userId, String modelId);
    ImportResult importBulk(String userId, ImportRequest req);
    ExportBundle export(String userId, List<String> ids);
}