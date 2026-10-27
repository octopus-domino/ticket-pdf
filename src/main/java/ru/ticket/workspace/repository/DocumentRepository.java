package ru.ticket.workspace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.ticket.workspace.domain.DocumentEntity;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {
    List<DocumentEntity> findAllByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<DocumentEntity> findByIdAndUserId(String id, String userId);
    List<DocumentEntity> findAllByUserIdAndFolderId(String userId, String folderId);
}