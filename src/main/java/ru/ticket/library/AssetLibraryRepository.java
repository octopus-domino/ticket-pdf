package ru.ticket.library;


import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AssetLibraryRepository extends JpaRepository<AssetLibrary,String> {
  interface Header {String getId();String getUserId();String getName();long getRevision();String getMembers();}
  List<Header> findAllProjectedBy();
  List<AssetLibrary> findByUserId(String userId);
  @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select l from AssetLibrary l where l.id=:id")
  Optional<AssetLibrary> locked(@Param("id") String id);
}
