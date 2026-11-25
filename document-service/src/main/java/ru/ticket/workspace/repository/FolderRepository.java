package ru.ticket.workspace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.ticket.workspace.domain.FolderEntity;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<FolderEntity, String> {
    List<FolderEntity> findAllByUserIdOrderByNameAsc(String userId);
    Optional<FolderEntity> findByIdAndUserId(String id, String userId);
}