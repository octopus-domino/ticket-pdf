package ru.ticket.journey;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.slf4j.*;
import org.springframework.stereotype.Component;

/** Streaming read-only GeoPackage import. Only one bounded batch is retained in memory. */
@Component
public class HoithImporter {
  private static final Logger log=LoggerFactory.getLogger(HoithImporter.class);
  private static final Pattern YEAR=Pattern.compile("(?<!\\d)(1\\d{3}|20\\d{2})(?!\\d)");
  private final BuildingHistoryService buildings;
  public HoithImporter(BuildingHistoryService buildings){this.buildings=buildings;}

  public long importFile(Path path,Set<String> cities) throws Exception {
    return importFile(path,cities,0);
  }
  public long importFile(Path path,Set<String> cities,int beforeYear) throws Exception {
    if(!java.nio.file.Files.isRegularFile(path)||!java.nio.file.Files.isReadable(path))throw new IOException("HOITH GeoPackage is not readable");
    var store=DataStoreFinder.getDataStore(Map.of("dbtype","geopkg","database",path.toAbsolutePath().toString(),"read_only",true,
        "memory map size",32,"max connections",1,"min connections",1,"fetch size",250));
    if(store==null)throw new IOException("No GeoPackage datastore available");
    long imported=0,skipped=0,pendingBytes=0;var batch=new ArrayList<BuildingHistory>(250);
    var wanted=cities.stream().map(v->v.strip().toLowerCase(Locale.ROOT)).filter(v->!v.isEmpty()).collect(java.util.stream.Collectors.toSet());
    try {
      var target=CRS.decode("EPSG:4326",true);
      for(String layer:store.getTypeNames()){
        var source=store.getFeatureSource(layer);var crs=source.getSchema().getCoordinateReferenceSystem();
        if(crs==null)throw new IOException("HOITH layer has no coordinate reference system");
        MathTransform transform=CRS.findMathTransform(crs,target,true);
        var query=new org.geotools.api.data.Query(layer);
        var factory=org.geotools.factory.CommonFactoryFinder.getFilterFactory();
        var restrictions=new ArrayList<org.geotools.api.filter.Filter>();
        if(!wanted.isEmpty()){
          var filters=new ArrayList<org.geotools.api.filter.Filter>();
          for(String field:List.of("city_russian","city_english","city"))if(source.getSchema().getDescriptor(field)!=null)
            for(String city:wanted)filters.add(factory.equal(factory.property(field),factory.literal(city),false));
          if(filters.isEmpty())throw new IOException("HOITH layer has no city fields for the requested filter");
          restrictions.add(filters.size()==1?filters.get(0):factory.or(filters));
        }
        if(beforeYear>0){
          if(source.getSchema().getDescriptor("r_year_int")==null)throw new IOException("HOITH layer has no construction year field");
          restrictions.add(factory.and(factory.greaterOrEqual(factory.property("r_year_int"),factory.literal(1000)),
              factory.lessOrEqual(factory.property("r_year_int"),factory.literal(beforeYear))));
        }
        if(!restrictions.isEmpty())query.setFilter(restrictions.size()==1?restrictions.get(0):factory.and(restrictions));
        // Push city filtering into SQLite; avoid decoding millions of discarded geometries in Java.
        try(var iterator=source.getFeatures(query).features()){
          while(iterator.hasNext()){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("HOITH import interrupted");
            var feature=iterator.next();
            if(!wanted.isEmpty()&&!wanted.contains(value(feature,"city_russian").toLowerCase(Locale.ROOT))
                &&!wanted.contains(value(feature,"city_english").toLowerCase(Locale.ROOT))&&!wanted.contains(value(feature,"city").toLowerCase(Locale.ROOT)))continue;
            BuildingHistory row;
            try {row=read(feature,transform,layer);}catch(Exception invalid){skipped++;continue;}
            if(row==null){skipped++;continue;}
            batch.add(row);
            pendingBytes+=(long)row.getGeometry().getNumPoints()*64+4096;
            if(batch.size()==250||pendingBytes>=8*1024*1024){
              imported+=buildings.upsertHoith(batch);batch.clear();pendingBytes=0;
              if(imported%10000==0)log.info("hoith.import progress={}",imported);
            }
          }
        }
      }
      if(!batch.isEmpty())imported+=buildings.upsertHoith(batch);
      log.info("Imported {} building records; skipped {} rows",imported,skipped);return imported;
    }finally{store.dispose();}
  }
  static BuildingHistory read(SimpleFeature feature,MathTransform transform,String layer) throws Exception {
    if(!(feature.getDefaultGeometry() instanceof Geometry raw)||raw.isEmpty()||raw.getNumPoints()>50000)return null;
    Geometry geometry=transform.isIdentity()?(Geometry)raw.copy():JTS.transform(raw,transform);
    if(!geometry.isValid())geometry=GeometryFixer.fix(geometry);
    if(geometry.isEmpty())return null;
    geometry.setSRID(4326);var envelope=geometry.getEnvelopeInternal();
    if(!Double.isFinite(envelope.getMinX())||!Double.isFinite(envelope.getMinY())||envelope.getMinX()< -180||envelope.getMaxX()>180||envelope.getMinY()< -85||envelope.getMaxY()>85)return null;
    var row=new BuildingHistory();row.setSource("HOITH");
    String id=value(feature,"osm_id","osm_way_id");
    if(id.isBlank())id="hoith:"+layer+":"+valueOr(feature.getID(),value(feature,"fid","id"));
    if(id.length()>50)id="hoith:"+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(id.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0,40);
    row.setOsmId(id);row.setAddress(cut(value(feature,"r_adress","r_address","address"),2000));
    row.setConstructionYear(year(value(feature,"r_year_int","construction_year","r_years_str","start_date")));
    row.setArchitect(cut(value(feature,"r_architect","architect"),300));row.setStyle(cut(value(feature,"r_style","style"),100));
    row.setHeritageStatus(cut(value(feature,"r_mkrf","heritage_status","heritage"),100));
    String url=value(feature,"r_url","r_wikipedia","r_cw_url","source_url");
    try {var uri=java.net.URI.create(url);if(!"https".equals(uri.getScheme())&&!"http".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)url="";}catch(IllegalArgumentException invalid){url="";}
    row.setSourceUrl(url.isBlank()?"https://disk.yandex.ru/d/6rI165UaOpvG0A":cut(url,2000));row.setGeometry(geometry);return row;
  }
  static Integer year(String value){
    if(value==null)return null;var matcher=YEAR.matcher(value);if(!matcher.find())return null;
    int year=Integer.parseInt(matcher.group());return year<=java.time.Year.now().getValue()?year:null;
  }
  private static String value(SimpleFeature feature,String...names){
    for(String name:names){if(feature.getFeatureType().getDescriptor(name)==null)continue;Object raw=feature.getAttribute(name);
      if(raw!=null&&!raw.toString().isBlank())return raw.toString().strip();}return "";
  }
  private static String valueOr(String fallback,String value){return value.isBlank()?fallback:value;}
  private static String cut(String value,int max){return value.length()>max?value.substring(0,max):value;}
}
