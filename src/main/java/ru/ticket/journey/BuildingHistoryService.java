package ru.ticket.journey;

import java.util.*;
import java.sql.Types;
import org.locationtech.jts.geom.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import ru.ticket.journey.walk.WalkThemeService.WalkCandidate;

@Service
public class BuildingHistoryService {
  private static final Logger log=LoggerFactory.getLogger(BuildingHistoryService.class);
  private final BuildingHistoryRepository repository;
  private final JdbcTemplate jdbc;
  private final boolean postgis;
  private final GeometryFactory geometry=new GeometryFactory(new PrecisionModel(),4326);
  public record Info(Long id,String osmId,String address,Integer constructionYear,String architect,String style,
      String heritageStatus,String source,String sourceUrl,double longitude,double latitude) {}
  public BuildingHistoryService(BuildingHistoryRepository repository,JdbcTemplate jdbc,@Value("${spring.datasource.url:}") String url){
    this.repository=repository;this.jdbc=jdbc;this.postgis=url.startsWith("jdbc:postgresql:");
  }
  @Transactional(readOnly=true)
  public Optional<BuildingHistory> findByPoint(double lon,double lat){
    validate(lon,lat);
    if(postgis)return repository.findByPoint(lon,lat);
    var point=geometry.createPoint(new Coordinate(lon,lat));
    return localRows().stream().filter(p->p.getGeometry()!=null&&p.getGeometry().covers(point))
        .sorted(Comparator.comparingInt((BuildingHistory p)->"SPB_PASSPORT".equals(p.getSource())?0:1)
            .thenComparingDouble(p->p.getGeometry().getArea())).findFirst();
  }
  @Transactional(readOnly=true)
  public List<Info> nearby(double lon,double lat,int limit,double radiusKm,String query,int beforeYear){
    validate(lon,lat);
    if(limit<1||limit>12||!Double.isFinite(radiusKm)||radiusKm<.1||radiusKm>3||query.length()>100||beforeYear<1||beforeYear>2100)
      throw ru.ticket.common.ApiException.badRequest("journey.route.invalid-route");
    String plain=query.strip().toLowerCase(Locale.ROOT);
    List<BuildingHistory> found;
    if(postgis){String escaped=plain.replace("!","!!").replace("%","!%").replace("_","!_");
      found=repository.findNearby(lon,lat,radiusKm*1000,beforeYear,plain.isEmpty()?"":"%"+escaped+"%",limit);
    }else {
      found=localRows().stream().filter(p->p.getGeometry()!=null&&!p.getGeometry().isEmpty())
          .filter(p->p.getConstructionYear()!=null&&p.getConstructionYear()<=beforeYear||p.getHeritageStatus()!=null&&!p.getHeritageStatus().isBlank())
          .filter(p->(Objects.toString(p.getAddress(),"")+" "+Objects.toString(p.getArchitect(),"")+" "+Objects.toString(p.getStyle(),"")).toLowerCase(Locale.ROOT).contains(plain))
          .filter(p->distance(lon,lat,p)<=radiusKm*1000).sorted(Comparator.comparingDouble(p->distance(lon,lat,p))).limit(limit).toList();
    }
    return found.stream().map(BuildingHistoryService::info).toList();
  }
  public static Info info(BuildingHistory p){
    Point point=p.getGeometry().getInteriorPoint();
    return new Info(p.getId(),p.getOsmId(),p.getAddress(),p.getConstructionYear(),p.getArchitect(),p.getStyle(),p.getHeritageStatus(),p.getSource(),p.getSourceUrl(),point.getX(),point.getY());
  }
  @Transactional(readOnly=true)
  public List<WalkCandidate> enrich(List<WalkCandidate> candidates,String language){
    var result=new ArrayList<WalkCandidate>(candidates.size());
    for(var point:candidates){
      try {
        var history=findByPoint(point.lon(),point.lat());
        if(history.isEmpty()){result.add(point);continue;}
        String detail=BuildingHistoryText.describe(info(history.get()),language);
        String description=(Objects.toString(point.description(),"")+"\n"+detail).strip();
        if(description.length()>1500)description=description.substring(0,1500);
        result.add(new WalkCandidate(point.source(),point.id(),point.label(),point.lon(),point.lat(),point.openingHours(),point.visitStatus(),point.wikidataSitelinks(),point.inOsm(),description,point.photos(),point.score(),point.wikidataId(),point.wikimapiaId(),point.sourceUrl()));
      }catch(org.springframework.dao.DataAccessException unavailable){log.warn("building.history enrichment_unavailable");result.add(point);break;}
    }
    if(result.size()<candidates.size())result.addAll(candidates.subList(result.size(),candidates.size()));
    return List.copyOf(result);
  }
  @Transactional(propagation=Propagation.REQUIRES_NEW)
  public int upsertHoith(List<BuildingHistory> batch){
    if(batch.size()>1000)throw new IllegalArgumentException("Building batch too large");
    if(postgis){
      jdbc.batchUpdate("""
          INSERT INTO building_history(osm_id,address,construction_year,architect,style,heritage_status,source,source_url,geometry)
          VALUES(?,?,?,?,?,?,'HOITH',?,ST_GeomFromText(?,4326))
          ON CONFLICT(source,osm_id) WHERE source='HOITH' AND osm_id IS NOT NULL
          DO UPDATE SET address=EXCLUDED.address,construction_year=EXCLUDED.construction_year,architect=EXCLUDED.architect,
            style=EXCLUDED.style,heritage_status=EXCLUDED.heritage_status,source_url=EXCLUDED.source_url,geometry=EXCLUDED.geometry,updated_at=NOW()
          """,batch,250,(statement,p)->{
            statement.setString(1,p.getOsmId());statement.setString(2,p.getAddress());
            if(p.getConstructionYear()==null)statement.setNull(3,Types.INTEGER);else statement.setInt(3,p.getConstructionYear());
            statement.setString(4,p.getArchitect());statement.setString(5,p.getStyle());statement.setString(6,p.getHeritageStatus());
            statement.setString(7,p.getSourceUrl());statement.setString(8,p.getGeometry().toText());
          });
    }else {
      for(var p:batch){
        var row=repository.findFirstBySourceAndOsmId("HOITH",p.getOsmId()).orElseGet(BuildingHistory::new);
        row.setOsmId(p.getOsmId());row.setSource("HOITH");row.setSourceUrl(p.getSourceUrl());row.setAddress(p.getAddress());
        row.setConstructionYear(p.getConstructionYear());row.setArchitect(p.getArchitect());row.setStyle(p.getStyle());row.setHeritageStatus(p.getHeritageStatus());row.setGeometry(p.getGeometry());repository.save(row);
      }
      repository.flush();
    }
    return batch.size();
  }
  private List<BuildingHistory> localRows(){return repository.findAll(PageRequest.of(0,5000)).getContent();}
  private static double distance(double lon,double lat,BuildingHistory p){
    var c=p.getGeometry().getInteriorPoint();
    double a=Math.pow(Math.sin(Math.toRadians(c.getY()-lat)/2),2)+Math.cos(Math.toRadians(lat))*Math.cos(Math.toRadians(c.getY()))*Math.pow(Math.sin(Math.toRadians(c.getX()-lon)/2),2);
    return 12742017.6*Math.asin(Math.sqrt(Math.min(1,a)));
  }
  private static void validate(double lon,double lat){if(!Double.isFinite(lon)||!Double.isFinite(lat)||Math.abs(lon)>=180||Math.abs(lat)>85)throw ru.ticket.common.ApiException.badRequest("journey.route.invalid-route");}
}
