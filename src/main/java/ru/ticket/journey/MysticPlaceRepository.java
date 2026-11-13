package ru.ticket.journey;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MysticPlaceRepository extends JpaRepository<MysticPlace,Long> {
  @Query(value="""
      SELECT * FROM mystic_places
      WHERE ST_DWithin(
        CAST(ST_SetSRID(ST_MakePoint(longitude, latitude),4326) AS geography),
        CAST(ST_SetSRID(ST_MakePoint(:lon,:lat),4326) AS geography), :radiusMeters)
      ORDER BY ST_Distance(
        CAST(ST_SetSRID(ST_MakePoint(longitude, latitude),4326) AS geography),
        CAST(ST_SetSRID(ST_MakePoint(:lon,:lat),4326) AS geography)), id
      LIMIT :maxResults
      """,nativeQuery=true)
  List<MysticPlace> findNearby(@Param("lon") double lon,@Param("lat") double lat,
      @Param("radiusMeters") double radiusMeters,@Param("maxResults") int maxResults);

  // H2 development uses a bounded box followed by an exact spherical distance check.
  @Query("select p from MysticPlace p where p.latitude between :south and :north and p.longitude between :west and :east")
  List<MysticPlace> findInBox(@Param("south") double south,@Param("north") double north,
      @Param("west") double west,@Param("east") double east);
  Optional<MysticPlace> findByNameAndLatitudeAndLongitude(String name,double latitude,double longitude);
}
