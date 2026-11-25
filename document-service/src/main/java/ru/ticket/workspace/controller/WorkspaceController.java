package ru.ticket.workspace.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.workspace.dto.WorkspaceDtos.*;
import ru.ticket.workspace.service.DocumentService;
import ru.ticket.workspace.service.FolderService;

import java.util.List;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final FolderService folderService;
    private final DocumentService documentService;

    public WorkspaceController(FolderService folderService, DocumentService documentService) {
        this.folderService = folderService;
        this.documentService = documentService;
    }

    /* ---------- tree ---------- */

    @GetMapping("/tree")
    public List<TreeNodeDto> tree(@AuthenticationPrincipal UserIdPrincipal me) {
        return folderService.buildTree(me.userId());
    }

    /* ---------- folders ---------- */

    @PostMapping("/folders")
    public ResponseEntity<TreeNodeDto> createFolder(@AuthenticationPrincipal UserIdPrincipal me,
                                                    @Valid @RequestBody CreateFolderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(folderService.create(me.userId(), req));
    }

    @PutMapping("/folders/{id}/name")
    public TreeNodeDto renameFolder(@AuthenticationPrincipal UserIdPrincipal me,
                                    @PathVariable String id,
                                    @Valid @RequestBody RenameRequest req) {
        return folderService.rename(me.userId(), id, req);
    }

    @PutMapping("/folders/{id}/parent")
    public TreeNodeDto moveFolder(@AuthenticationPrincipal UserIdPrincipal me,
                                  @PathVariable String id,
                                  @RequestBody MoveRequest req) {
        return folderService.move(me.userId(), id, req);
    }

    @DeleteMapping("/folders/{id}")
    public ResponseEntity<Void> deleteFolder(@AuthenticationPrincipal UserIdPrincipal me,
                                             @PathVariable String id) {
        folderService.delete(me.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /* ---------- documents ---------- */

    @PostMapping("/documents")
    public ResponseEntity<DocumentDto> createDocument(@AuthenticationPrincipal UserIdPrincipal me,
                                                      @Valid @RequestBody CreateDocumentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.create(me.userId(), req));
    }

    @GetMapping("/documents/{id}")
    public DocumentDto getDocument(@AuthenticationPrincipal UserIdPrincipal me,
                                   @PathVariable String id) {
        return documentService.get(me.userId(), id);
    }

    @PutMapping("/documents/{id}/content")
    public DocumentDto saveDocument(@AuthenticationPrincipal UserIdPrincipal me,
                                    @PathVariable String id,
                                    @Valid @RequestBody SaveDocumentRequest req) {
        return documentService.save(me.userId(), id, req.content());
    }

    @PutMapping("/documents/{id}/name")
    public DocumentDto renameDocument(@AuthenticationPrincipal UserIdPrincipal me,
                                      @PathVariable String id,
                                      @Valid @RequestBody RenameRequest req) {
        return documentService.rename(me.userId(), id, req);
    }

    @PutMapping("/documents/{id}/parent")
    public DocumentDto moveDocument(@AuthenticationPrincipal UserIdPrincipal me,
                                    @PathVariable String id,
                                    @RequestBody MoveRequest req) {
        return documentService.move(me.userId(), id, req);
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@AuthenticationPrincipal UserIdPrincipal me,
                                               @PathVariable String id) {
        documentService.delete(me.userId(), id);
        return ResponseEntity.noContent().build();
    }
}