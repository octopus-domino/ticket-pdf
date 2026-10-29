package ru.ticket.workspace.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;
import ru.ticket.workspace.domain.DocumentEntity;
import ru.ticket.workspace.domain.FolderEntity;
import ru.ticket.workspace.dto.WorkspaceDtos.CreateFolderRequest;
import ru.ticket.workspace.dto.WorkspaceDtos.MoveRequest;
import ru.ticket.workspace.dto.WorkspaceDtos.RenameRequest;
import ru.ticket.workspace.dto.WorkspaceDtos.TreeNodeDto;
import ru.ticket.workspace.repository.DocumentRepository;
import ru.ticket.workspace.repository.FolderRepository;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FolderServiceImpl implements FolderService {

    private static final String ROOT = "__root__";

    private final FolderRepository folders;
    private final DocumentRepository documents;

    public FolderServiceImpl(FolderRepository folders, DocumentRepository documents) {
        this.folders = folders;
        this.documents = documents;
    }

    @Override
    public List<TreeNodeDto> buildTree(String userId) {
        List<FolderEntity> allFolders = folders.findAllByUserIdOrderByNameAsc(userId);
        List<DocumentEntity> allDocs = documents.findAllByUserIdOrderByUpdatedAtDesc(userId);

        Map<String, List<FolderEntity>> foldersByParent = allFolders.stream()
                .collect(Collectors.groupingBy(f -> key(f.getParentId())));

        Map<String, List<DocumentEntity>> docsByFolder = allDocs.stream()
                .collect(Collectors.groupingBy(d -> key(d.getFolderId())));

        return buildChildren(ROOT, foldersByParent, docsByFolder);
    }

    @Override
    @Transactional
    public TreeNodeDto create(String userId, CreateFolderRequest req) {
        String parentId = normalizeParent(req.parentId());
        if (parentId != null) {
            folders.findByIdAndUserId(parentId, userId)
                    .orElseThrow(() -> ApiException.notFound("Parent folder"));
        }
        FolderEntity folder = folders.save(new FolderEntity(userId, parentId, req.name().trim()));
        return new TreeNodeDto(folder.getId(), "folder", folder.getName(),
                folder.getParentId(), null, List.of());
    }

    @Override
    @Transactional
    public TreeNodeDto rename(String userId, String folderId, RenameRequest req) {
        FolderEntity folder = folders.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> ApiException.notFound("Folder"));
        folder.rename(req.name().trim());
        folders.save(folder);
        return new TreeNodeDto(folder.getId(), "folder", folder.getName(),
                folder.getParentId(), null, List.of());
    }

    @Override
    @Transactional
    public TreeNodeDto move(String userId, String folderId, MoveRequest req) {
        FolderEntity folder = folders.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> ApiException.notFound("Folder"));
        String newParentId = normalizeParent(req.parentId());

        if (newParentId != null) {
            folders.findByIdAndUserId(newParentId, userId)
                    .orElseThrow(() -> ApiException.notFound("Parent folder"));

            String cursor = newParentId;
            while (cursor != null) {
                if (cursor.equals(folderId)) {
                    throw ApiException.badRequest("Cannot move folder into its own descendant");
                }
                FolderEntity c = folders.findById(cursor).orElse(null);
                cursor = c == null ? null : c.getParentId();
            }
        }

        folder.moveTo(newParentId);
        folders.save(folder);
        return new TreeNodeDto(folder.getId(), "folder", folder.getName(),
                folder.getParentId(), null, List.of());
    }

    @Override
    @Transactional
    public void delete(String userId, String folderId) {
        FolderEntity root = folders.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> ApiException.notFound("Folder"));

        List<FolderEntity> allFolders = folders.findAllByUserIdOrderByNameAsc(userId);
        Map<String, List<String>> childrenByParent = new HashMap<>();
        for (FolderEntity f : allFolders) {
            String parent = f.getParentId() == null ? ROOT : f.getParentId();
            childrenByParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(f.getId());
        }

        List<DocumentEntity> allDocs = documents.findAllByUserIdOrderByUpdatedAtDesc(userId);
        Map<String, List<DocumentEntity>> docsByFolder = new HashMap<>();
        for (DocumentEntity d : allDocs) {
            String parent = d.getFolderId() == null ? ROOT : d.getFolderId();
            docsByFolder.computeIfAbsent(parent, k -> new ArrayList<>()).add(d);
        }

        List<String> folderIdsToDelete = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(root.getId());

        while (!queue.isEmpty()) {
            String current = queue.poll();
            folderIdsToDelete.add(current);
            List<String> children = childrenByParent.get(current);
            if (children != null) queue.addAll(children);
        }

        List<DocumentEntity> docsToDelete = new ArrayList<>();
        for (String fid : folderIdsToDelete) {
            List<DocumentEntity> inFolder = docsByFolder.get(fid);
            if (inFolder != null) docsToDelete.addAll(inFolder);
        }

        if (!docsToDelete.isEmpty()) {
            documents.deleteAll(docsToDelete);
            documents.flush();
        }

        folders.deleteAllByIdInBatch(folderIdsToDelete);
    }

    private List<TreeNodeDto> buildChildren(
            String parentKey,
            Map<String, List<FolderEntity>> foldersByParent,
            Map<String, List<DocumentEntity>> docsByFolder) {

        List<TreeNodeDto> result = new ArrayList<>();

        for (FolderEntity f : foldersByParent.getOrDefault(parentKey, List.of())) {
            result.add(new TreeNodeDto(
                    f.getId(), "folder", f.getName(), f.getParentId(), null,
                    buildChildren(f.getId(), foldersByParent, docsByFolder)
            ));
        }
        for (DocumentEntity d : docsByFolder.getOrDefault(parentKey, List.of())) {
            result.add(new TreeNodeDto(
                    d.getId(), "document", d.getName(), d.getFolderId(),
                    d.getUpdatedAt(), List.of()
            ));
        }
        return result;
    }

    private String normalizeParent(String parentId) {
        return (parentId == null || parentId.isBlank() || "root".equals(parentId))
                ? null : parentId;
    }

    private String key(String parentId) {
        return parentId == null ? ROOT : parentId;
    }
}