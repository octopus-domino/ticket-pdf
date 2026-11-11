package ru.ticket.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRepository extends JpaRepository<WorkflowEntity, String> {
  interface Summary {
    String getSlug();

    String getDraft();

    String getPublished();

    boolean getShared();

    long getRevision();

    long getPublishedRevision();
  }

  List<Summary> findTop100ByOwnerIdOrderByUpdatedAtDesc(String ownerId);

  List<Summary> findTop100BySharedTrueAndPublishedIsNotNullOrderByUpdatedAtDesc();
  List<Summary> findTop100BySharedTrueOrderByUpdatedAtDesc();

  long countByOwnerId(String ownerId);
}
