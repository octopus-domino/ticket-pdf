package ru.ticket.workspace.service;

import ru.ticket.workspace.dto.WorkspaceDtos.CreateFolderRequest;
import ru.ticket.workspace.dto.WorkspaceDtos.MoveRequest;
import ru.ticket.workspace.dto.WorkspaceDtos.RenameRequest;
import ru.ticket.workspace.dto.WorkspaceDtos.TreeNodeDto;

import java.util.List;

public interface FolderService {
    List<TreeNodeDto> buildTree(String userId);
    TreeNodeDto create(String userId, CreateFolderRequest req);
    TreeNodeDto rename(String userId, String folderId, RenameRequest req);
    TreeNodeDto move(String userId, String folderId, MoveRequest req);
    void delete(String userId, String folderId);
}