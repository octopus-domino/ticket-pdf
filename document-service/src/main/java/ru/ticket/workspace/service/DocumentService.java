package ru.ticket.workspace.service;

import ru.ticket.workspace.dto.WorkspaceDtos.CreateDocumentRequest;
import ru.ticket.workspace.dto.WorkspaceDtos.DocumentDto;
import ru.ticket.workspace.dto.WorkspaceDtos.MoveRequest;
import ru.ticket.workspace.dto.WorkspaceDtos.RenameRequest;

public interface DocumentService {
    DocumentDto create(String userId, CreateDocumentRequest req);
    DocumentDto get(String userId, String docId);
    DocumentDto save(String userId, String docId, String content);
    DocumentDto rename(String userId, String docId, RenameRequest req);
    DocumentDto move(String userId, String docId, MoveRequest req);
    void delete(String userId, String docId);
}