package ru.ticket.clientapi;


import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ClientApiKeyRepository extends JpaRepository<ClientApiKey, String> {
  List<ClientApiKey> findByUserIdOrderByCreatedAtDesc(String userId);
  Optional<ClientApiKey> findByIdAndUserId(String id, String userId);
  @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select k from ClientApiKey k where k.tokenHash=:hash")
  Optional<ClientApiKey> lockByHash(@Param("hash") String hash);
}
