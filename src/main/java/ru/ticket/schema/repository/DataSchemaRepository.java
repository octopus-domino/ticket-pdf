package ru.ticket.schema.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.ticket.schema.domain.DataSchemaEntity;

import java.util.List;
import java.util.Optional;

public interface DataSchemaRepository extends JpaRepository<DataSchemaEntity, String> {
    List<DataSchemaEntity> findAllByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<DataSchemaEntity> findByIdAndUserId(String id, String userId);
    boolean existsByIdAndUserId(String id, String userId);
}