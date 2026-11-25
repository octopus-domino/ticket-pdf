package ru.ticket.datamodel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.ticket.datamodel.domain.DataModelEntity;

import java.util.List;
import java.util.Optional;

public interface DataModelRepository extends JpaRepository<DataModelEntity, String> {
    List<DataModelEntity> findAllByUserIdOrderByUpdatedAtDesc(String userId);
    List<DataModelEntity> findAllByUserIdAndSchemaIdOrderByUpdatedAtDesc(String userId, String schemaId);
    Optional<DataModelEntity> findByIdAndUserId(String id, String userId);
    long countBySchemaId(String schemaId);
    List<DataModelEntity> findAllByUserIdAndIdIn(String userId, List<String> ids);
}