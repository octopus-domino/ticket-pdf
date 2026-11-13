package ru.ticket.journey;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface JourneyAlbumRepository extends JpaRepository<JourneyAlbum, String> {
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select album from JourneyAlbum album where album.id = :id")
  Optional<JourneyAlbum> lockById(@Param("id") String id);
}
