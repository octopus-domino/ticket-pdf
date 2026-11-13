package ru.ticket.journey;

import java.sql.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Optional native SQL contract check against an explicitly supplied disposable test database. */
@EnabledIfEnvironmentVariable(named="CODOC_POSTGIS_TEST_URL",matches="jdbc:postgresql:.*")
class PostgisPlacesTest {
  @Test void migrationsAndSpatialPredicatesUseMetersAndIncludePolygonBoundaries() throws Exception {
    String url=System.getenv("CODOC_POSTGIS_TEST_URL");
    assertTrue(url.matches("jdbc:postgresql://[^/]+/[a-zA-Z0-9_]*test[a-zA-Z0-9_]*"),"Only a dedicated test database is permitted");
    try(var db=DriverManager.getConnection(url,System.getenv("CODOC_POSTGIS_TEST_USER"),System.getenv("CODOC_POSTGIS_TEST_PASSWORD"))){
      db.setAutoCommit(false);
      try(var statement=db.createStatement()){
        for(String name:new String[]{"upgrade-mystic-places.sql","upgrade-building-history.sql"})
          statement.execute(Files.readString(Path.of("src/main/resources/db",name)));
        statement.execute("INSERT INTO mystic_places(name,longitude,latitude) VALUES('Test close',30.315,59.938),('Test far',30.315,60.938)");
        try(var rows=statement.executeQuery("SELECT count(*) FROM mystic_places WHERE name LIKE 'Test %' AND ST_DWithin(ST_SetSRID(ST_MakePoint(longitude,latitude),4326)::geography,ST_SetSRID(ST_MakePoint(30.315,59.938),4326)::geography,1500)")){
          assertTrue(rows.next());assertEquals(1,rows.getInt(1));
        }
        statement.execute("INSERT INTO building_history(source,osm_id,geometry) VALUES('HOITH','test-native',ST_GeomFromText('POLYGON((30.314 59.937,30.316 59.937,30.316 59.939,30.314 59.939,30.314 59.937))',4326))");
        try(var rows=statement.executeQuery("SELECT ST_Covers(geometry,ST_SetSRID(ST_MakePoint(30.314,59.937),4326)) FROM building_history WHERE osm_id='test-native'")){
          assertTrue(rows.next());assertTrue(rows.getBoolean(1));
        }
      }finally{db.rollback();}
    }
  }
}
