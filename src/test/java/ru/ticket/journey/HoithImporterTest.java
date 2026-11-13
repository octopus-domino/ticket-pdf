package ru.ticket.journey;

import org.geotools.feature.simple.*;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;

class HoithImporterTest {
  @TempDir Path temporary;
  @Test void streamsARealGeoPackageAndFiltersCities() throws Exception {
    var path=temporary.resolve("fixture.gpkg");
    var types=new SimpleFeatureTypeBuilder();types.setName("buildings");types.setCRS(CRS.decode("EPSG:4326",true));
    types.add("geom",Polygon.class);types.add("r_year_int",Double.class);types.add("r_adress",String.class);types.add("city_russian",String.class);
    var schema=types.buildFeatureType();
    var store=org.geotools.api.data.DataStoreFinder.getDataStore(Map.of("dbtype","geopkg","database",path.toString()));
    assertNotNull(store);
    try {
      store.createSchema(schema);
      var features=new org.geotools.feature.DefaultFeatureCollection(null,schema);
      var polygon=new GeometryFactory().createPolygon(new Coordinate[]{new Coordinate(30.3,59.9),new Coordinate(30.31,59.9),new Coordinate(30.31,59.91),new Coordinate(30.3,59.91),new Coordinate(30.3,59.9)});
      for(String city:List.of("Selected","Other")){
        var values=new SimpleFeatureBuilder(schema);values.addAll(new Object[]{polygon,1900.0,"Fixture "+city,city});features.add(values.buildFeature(city));
      }
      ((org.geotools.api.data.SimpleFeatureStore)store.getFeatureSource("buildings")).addFeatures(features);
    }finally{store.dispose();}
    var service=mock(BuildingHistoryService.class);
    when(service.upsertHoith(anyList())).thenAnswer(call->{List<BuildingHistory> rows=call.getArgument(0);assertEquals(1,rows.size());assertEquals("Fixture Selected",rows.get(0).getAddress());return rows.size();});
    assertEquals(1,new HoithImporter(service).importFile(path,Set.of("Selected")));
    verify(service,times(1)).upsertHoith(anyList());
  }
  @Test void mapsActualHoithColumnsTransforms3857AndUsesStableFeatureIds() throws Exception {
    var types=new SimpleFeatureTypeBuilder();types.setName("all_cities_181123");types.setCRS(CRS.decode("EPSG:3857",true));
    types.add("geom",Polygon.class);types.add("r_year_int",Double.class);types.add("r_adress",String.class);types.add("r_architect",String.class);types.add("r_style",String.class);types.add("fid",Long.class);
    var schema=types.buildFeatureType();var values=new SimpleFeatureBuilder(schema);
    var geometry=new GeometryFactory().createPolygon(new Coordinate[]{new Coordinate(3374550,8389700),new Coordinate(3374570,8389700),new Coordinate(3374570,8389720),new Coordinate(3374550,8389720),new Coordinate(3374550,8389700)});
    values.addAll(new Object[]{geometry,1901.0,"Fixture address","Fixture architect","Fixture style",123L});
    var feature=values.buildFeature("all_cities_181123.123");
    var transform=CRS.findMathTransform(schema.getCoordinateReferenceSystem(),CRS.decode("EPSG:4326",true),true);
    var row=HoithImporter.read(feature,transform,"all_cities_181123");
    assertEquals(1901,row.getConstructionYear());assertEquals("Fixture architect",row.getArchitect());assertEquals("hoith:all_cities_181123:123",row.getOsmId());
    assertEquals(4326,row.getGeometry().getSRID());assertTrue(row.getGeometry().getCentroid().getX()>30&&row.getGeometry().getCentroid().getX()<31);
    assertTrue(row.getGeometry().getCentroid().getY()>59&&row.getGeometry().getCentroid().getY()<61);
    assertEquals(row.getOsmId(),HoithImporter.read(feature,transform,"all_cities_181123").getOsmId());
    assertNull(HoithImporter.year("unknown"));assertEquals(1890,HoithImporter.year("1890–1895"));assertNull(HoithImporter.year("0.0"));
  }
}
