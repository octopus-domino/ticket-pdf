package ru.ticket.workflow;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface WorkflowVisitRepository extends JpaRepository<WorkflowVisit, String> {
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select visit from WorkflowVisit visit where visit.id = :id")
  Optional<WorkflowVisit> lockById(@Param("id") String id);
}
