package ru.ticket.template.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.ticket.template.domain.TemplateEntity;

public interface TemplateRepository extends JpaRepository<TemplateEntity, String> {

  List<TemplateEntity> findAllByUserIdOrderByUpdatedAtDesc(String userId);

  List<TemplateEntity> findAllByUserIdAndSchemaIdOrderByUpdatedAtDesc(
      String userId, String schemaId);

  Optional<TemplateEntity> findByIdAndUserId(String id, String userId);

  long countBySchemaId(String schemaId);
}
