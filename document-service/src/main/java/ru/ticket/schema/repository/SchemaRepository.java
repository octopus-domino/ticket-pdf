package ru.ticket.schema.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.ticket.schema.domain.SchemaEntity;

import java.util.List;
import java.util.Optional;

public interface SchemaRepository extends JpaRepository<SchemaEntity, String> {
    List<SchemaEntity> findAllByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<SchemaEntity> findByIdAndUserId(String id, String userId);
    boolean existsByIdAndUserId(String id, String userId);
}