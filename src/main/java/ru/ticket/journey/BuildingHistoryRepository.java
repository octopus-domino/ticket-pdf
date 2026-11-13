package ru.ticket.journey;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BuildingHistoryRepository extends JpaRepository<BuildingHistory,Long> {
  @Query(value="""
      SELECT * FROM building_history bh
      WHERE ST_Covers(bh.geometry,ST_SetSRID(ST_MakePoint(:lon,:lat),4326))
      ORDER BY CASE bh.source WHEN 'SPB_PASSPORT' THEN 0 ELSE 1 END, ST_Area(bh.geometry), bh.id LIMIT 1
      """,nativeQuery=true)
  Optional<BuildingHistory> findByPoint(@Param("lon") double lon,@Param("lat") double lat);

  @Query(value="""
      SELECT * FROM building_history bh WHERE
      (bh.construction_year <= :beforeYear OR COALESCE(bh.heritage_status,'') <> '')
      AND ST_DWithin(CAST(bh.geometry AS geography),CAST(ST_SetSRID(ST_MakePoint(:lon,:lat),4326) AS geography),:radius)
      AND (:query='' OR lower(COALESCE(bh.address,'')||' '||COALESCE(bh.architect,'')||' '||COALESCE(bh.style,'')) LIKE :query ESCAPE '!')
      ORDER BY ST_Distance(CAST(bh.geometry AS geography),CAST(ST_SetSRID(ST_MakePoint(:lon,:lat),4326) AS geography)),bh.id
      LIMIT :limit
      """,nativeQuery=true)
  List<BuildingHistory> findNearby(@Param("lon") double lon,@Param("lat") double lat,@Param("radius") double radius,
      @Param("beforeYear") int beforeYear,@Param("query") String query,@Param("limit") int limit);
  Optional<BuildingHistory> findFirstBySourceAndOsmId(String source,String osmId);
  long countBySource(String source);
}
