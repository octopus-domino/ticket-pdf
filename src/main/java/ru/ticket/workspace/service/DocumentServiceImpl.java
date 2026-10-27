package ru.ticket.workspace.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;
import ru.ticket.workspace.domain.DocumentEntity;
import ru.ticket.workspace.dto.WorkspaceDtos.CreateDocumentRequest;
import ru.ticket.workspace.dto.WorkspaceDtos.DocumentDto;
import ru.ticket.workspace.dto.WorkspaceDtos.MoveRequest;
import ru.ticket.workspace.dto.WorkspaceDtos.RenameRequest;
import ru.ticket.workspace.repository.DocumentRepository;
import ru.ticket.workspace.repository.FolderRepository;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final String EMPTY_CONTENT = "{\"slides\":[],\"data\":[]}";

    private final DocumentRepository documents;
    private final FolderRepository folders;

    public DocumentServiceImpl(DocumentRepository documents, FolderRepository folders) {
        this.documents = documents;
        this.folders = folders;
    }

    @Override
    @Transactional
    public DocumentDto create(String userId, CreateDocumentRequest req) {
        String folderId = normalizeFolder(req.folderId());
        if (folderId != null) {
            folders.findByIdAndUserId(folderId, userId)
                    .orElseThrow(() -> ApiException.notFound("Folder"));
        }
        DocumentEntity doc = documents.save(new DocumentEntity(userId, folderId, req.name().trim(), EMPTY_CONTENT));
        return toDto(doc);
    }

    @Override
    public DocumentDto get(String userId, String docId) {
        return toDto(find(userId, docId));
    }

    @Override
    @Transactional
    public DocumentDto save(String userId, String docId, String content) {
        DocumentEntity doc = find(userId, docId);
        doc.saveContent(content);
        documents.save(doc);
        return toDto(doc);
    }

    @Override
    @Transactional
    public DocumentDto rename(String userId, String docId, RenameRequest req) {
        DocumentEntity doc = find(userId, docId);
        doc.rename(req.name().trim());
        documents.save(doc);
        return toDto(doc);
    }

    @Override
    @Transactional
    public DocumentDto move(String userId, String docId, MoveRequest req) {
        DocumentEntity doc = find(userId, docId);
        String folderId = normalizeFolder(req.parentId());
        if (folderId != null) {
            folders.findByIdAndUserId(folderId, userId)
                    .orElseThrow(() -> ApiException.notFound("Folder"));
        }
        doc.moveTo(folderId);
        documents.save(doc);
        return toDto(doc);
    }

    @Override
    @Transactional
    public void delete(String userId, String docId) {
        DocumentEntity doc = find(userId, docId);
        documents.delete(doc);
    }

    private DocumentEntity find(String userId, String docId) {
        return documents.findByIdAndUserId(docId, userId)
                .orElseThrow(() -> ApiException.notFound("Document"));
    }

    private String normalizeFolder(String folderId) {
        return (folderId == null || folderId.isBlank() || "root".equals(folderId))
                ? null : folderId;
    }

    private DocumentDto toDto(DocumentEntity d) {
        return new DocumentDto(d.getId(), d.getName(), d.getFolderId(), d.getContent(), d.getUpdatedAt());
    }
}