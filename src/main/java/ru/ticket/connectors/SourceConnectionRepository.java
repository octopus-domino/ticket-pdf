package ru.ticket.connectors;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SourceConnectionRepository extends JpaRepository<SourceConnection, String> {
  List<SourceConnection> findByUserIdOrderByName(String userId);
  List<SourceConnection> findByAutoSyncMinutesGreaterThan(int minutes);
  Optional<SourceConnection> findByIdAndUserId(String id, String userId);
  @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from SourceConnection s where s.id=:id and s.userId=:owner")
  Optional<SourceConnection> lockOwned(@Param("id") String id, @Param("owner") String owner);
  @org.springframework.transaction.annotation.Transactional
  @Modifying @Query("update SourceConnection s set s.lastSyncAt=:time,s.lastStatus=:status where s.id=:id")
  void markStatus(@Param("id") String id,@Param("time") java.time.Instant time,@Param("status") String status);
}
