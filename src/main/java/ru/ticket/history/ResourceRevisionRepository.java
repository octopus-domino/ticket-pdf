package ru.ticket.history;

import java.util.*;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface ResourceRevisionRepository extends JpaRepository<ResourceRevision,String> {
  interface Header { String getId(); Instant getCreatedAt(); String getContentHash(); int getPayloadBytes(); }
  List<Header> findByUserIdAndKindAndResourceIdOrderByCreatedAtDesc(String userId,String kind,String resourceId);
  Optional<ResourceRevision> findFirstByUserIdAndKindAndResourceIdOrderByCreatedAtDesc(String userId,String kind,String resourceId);
  Optional<ResourceRevision> findByIdAndUserIdAndKindAndResourceId(String id,String userId,String kind,String resourceId);
  List<ResourceRevision> findByUserIdOrderByCreatedAtAsc(String userId,Pageable page);
  @Query("select coalesce(sum(r.payloadBytes),0) from ResourceRevision r where r.userId=?1") long totalBytes(String userId);
  long deleteByCreatedAtBefore(Instant date);
}
