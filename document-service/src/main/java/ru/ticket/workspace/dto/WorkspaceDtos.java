package ru.ticket.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class WorkspaceDtos {
    private WorkspaceDtos() { }

    public record TreeNodeDto(
            String id,
            String type,       // "folder" | "document"
            String name,
            String parentId,
            Instant updatedAt,
            List<TreeNodeDto> children
    ) { }

    public record DocumentDto(
            String id,
            String name,
            String folderId,
            String content,
            Instant updatedAt
    ) { }

    public record CreateFolderRequest(
            @NotBlank @Size(max = 100) String name,
            String parentId
    ) { }

    public record CreateDocumentRequest(
            @NotBlank @Size(max = 200) String name,
            String folderId
    ) { }

    public record RenameRequest(@NotBlank @Size(max = 200) String name) { }

    public record MoveRequest(String parentId) { }

    public record SaveDocumentRequest(@NotBlank String content) { }
}