package ru.ticket.journey.walk;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;
import ru.ticket.journey.*;
import ru.ticket.mcp.OsmMcpClient;
import static ru.ticket.journey.walk.WikimapiaPoiSource.*;

@Service
public class WalkThemeService {
  private static final Logger log = LoggerFactory.getLogger(WalkThemeService.class);
  private static final Duration TOTAL_BUDGET = Duration.ofSeconds(12);
  private static final long OSM_AWAIT_MS = 8000;
  private static final long WIKIMAPIA_AWAIT_MS = 2500;
  private static final Duration ENRICH_TAIL = Duration.ofSeconds(3);

  public record WalkRequest(double startLon,double startLat,String theme,LocalDateTime visitTime,String language,
                            int maxStops,double radiusKm,Integer maxSitelinks) {
    public WalkRequest(double lon,double lat,String theme,LocalDateTime time,String language,int stops,double radius){this(lon,lat,theme,time,language,stops,radius,null);}
  }
  public record WalkCandidate(String source,String id,String label,double lon,double lat,String openingHours,
                              String visitStatus,Integer wikidataSitelinks,boolean inOsm,String description,List<WikimapiaPhoto> photos,
                              double score,String wikidataId,String wikimapiaId) {}

  private final WalkThemeProperties themes;
  private final OsmPoiSource osm;
  private final PhotonPoiSource photon;
  private final WikimapiaPoiSource wikimapia;
  private final OpeningHoursEvaluator hours;
  private final WikidataPopularity popularity;
  private final Semaphore permit = new Semaphore(1);
  private final ExecutorService discovery = Executors.newFixedThreadPool(2,
          r -> { var t = new Thread(r, "walk-discovery"); t.setDaemon(true); return t; });
  private final WalkCache<String, List<WalkCandidate>> cache = new WalkCache<>(64, Duration.ofMinutes(10));
  private final int detailsLimit, minDescription;

  @Value("${app.journeys.max-wikidata-sitelinks:5}")
  private int defaultSitelinks = 5;

  public WalkThemeService(WalkThemeProperties themes, OsmPoiSource osm, PhotonPoiSource photon,
                          WikimapiaPoiSource wikimapia, OpeningHoursEvaluator hours, WikidataPopularity popularity,
                          @Value("${app.journeys.wikimapia.max-details-per-request:25}") int detailsLimit,
                          @Value("${app.journeys.wikimapia.min-description-length:20}") int minDescription){
    this.themes=themes; this.osm=osm; this.photon=photon; this.wikimapia=wikimapia; this.hours=hours; this.popularity=popularity;
    this.detailsLimit=Math.max(0,Math.min(25,detailsLimit)); this.minDescription=Math.max(0,minDescription);
  }

  public List<WalkCandidate> findCandidates(WalkRequest request){
    if(!Double.isFinite(request.startLon())||!Double.isFinite(request.startLat())||Math.abs(request.startLon())>=180||Math.abs(request.startLat())>85)
      throw ApiException.badRequest("journey.route.invalid-route");
    var theme = themes.require(request.theme());
    if(request.maxStops()<7||request.maxStops()>12||!Double.isFinite(request.radiusKm())||request.radiusKm()<.1||request.radiusKm()>3
            ||request.language()==null||!Set.of("en","ru","de","fr","it","es","pl","nl","zh").contains(request.language())
            ||request.maxSitelinks()!=null&&(request.maxSitelinks()<0||request.maxSitelinks()>10000))
      throw ApiException.badRequest("journey.route.invalid-route");

    String key=request.toString();
    var cached=cache.get(key); if(cached!=null)return cached;
    if(!permit.tryAcquire()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"journey.route.search-busy");

    long started=System.nanoTime();
    long deadline=started + TOTAL_BUDGET.toNanos();
    Future<List<OsmMcpClient.Poi>> osmTask=null;
    Future<List<WikimapiaPoi>> wikiTask=null;
    try(var budget=WalkBudget.until(deadline)){
      double dy=request.radiusKm()/111.195, dx=dy/Math.cos(Math.toRadians(request.startLat()));
      final long scopeDeadline=deadline;

      osmTask=discovery.submit(()->{
        try(var scope=WalkBudget.until(scopeDeadline)){
          List<OsmMcpClient.Poi> result;
          try { result=osm.search(request.startLon(),request.startLat(),(int)(request.radiusKm()*1000),theme,request.language()); }
          catch(java.io.IOException unavailable) { result=List.of(); }
          if(result.isEmpty() && !Thread.currentThread().isInterrupted()){
            try{
              var fallback=photon.search(request.startLon(),request.startLat(),(int)(request.radiusKm()*1000),theme,request.language());
              if(!fallback.isEmpty()){ log.info("walk.photon_fallback count={}", fallback.size()); return fallback; }
            }catch(java.io.IOException ex){ log.debug("walk.photon_unavailable"); }
          }
          return result;
        }
      });

      wikiTask=discovery.submit(()->{
        try(var scope=WalkBudget.until(scopeDeadline)){
          return wikimapia.enabled()?wikimapia.fetchInBox(request.startLon()-dx,request.startLat()-dy,request.startLon()+dx,request.startLat()+dy,theme,request.language()):List.of();
        }
      });

      var osmPoints=await(osmTask,deadline,OSM_AWAIT_MS);
      var wikiPoints=await(wikiTask,deadline,WIKIMAPIA_AWAIT_MS).stream()
              .filter(p->WikimapiaPoiSourceImpl.distance(request.startLon(),request.startLat(),p.lon(),p.lat())<=request.radiusKm()*1000)
              .sorted(Comparator.comparingDouble((WikimapiaPoi p)->preScore(p,theme)).reversed())
              .limit(detailsLimit).toList();

      var wikiCandidates=new ArrayList<WalkCandidate>();
      for(var point:wikiPoints){
        if(System.nanoTime()>deadline-ENRICH_TAIL.toNanos())break;
        var details=wikimapia.enrich(point.id(),request.language());
        // Null includes deliberately rejected large areas: never resurrect box categories afterwards.
        if(details==null)continue;
        List<String> categories=details==null?point.categories():details.categoryNames();
        String description=details==null?"":details.description();
        double match=WikimapiaPoiSourceImpl.matches(categories,theme.wikimapiaCategories())?1
                :description.length()>=minDescription&&WikimapiaPoiSourceImpl.matches(List.of(description),theme.descriptionKeywords())?.5:0;
        if(match==0)continue;
        var photos=details==null?List.<WikimapiaPhoto>of():details.photos();
        wikiCandidates.add(new WalkCandidate("wikimapia","wikimapia-"+point.id(),point.name(),point.lon(),point.lat(),"","UNKNOWN",null,true,description,photos,match,"",Long.toString(point.id())));
      }

      var acceptedIds=wikiCandidates.stream().map(WalkCandidate::wikimapiaId).collect(java.util.stream.Collectors.toSet());
      var crossCheck=wikimapia.crossCheckOsm(wikiPoints.stream().filter(p->acceptedIds.contains(Long.toString(p.id()))).toList());
      var counts=popularity.counts(osmPoints.stream().map(p->p.tags().get("wikidata")).filter(Objects::nonNull).toList());

      var candidates=new ArrayList<WalkCandidate>();
      for(var point:osmPoints){
        if(!OsmPoiSource.matches(point.tags(),theme)||WikimapiaPoiSourceImpl.distance(request.startLon(),request.startLat(),point.lon(),point.lat())>request.radiusKm()*1000)continue;
        String wikidata=point.tags().getOrDefault("wikidata","");Integer count=counts.get(wikidata);
        if(count!=null&&count>(request.maxSitelinks()==null?defaultSitelinks:request.maxSitelinks()))continue;
        String opening=point.tags().getOrDefault("opening_hours","");String status=hours.evaluate(opening,request.visitTime()).name();
        double bonus=wikidata.isBlank()&&!"false".equals(point.tags().get("codoc:details_verified"))?.3:count!=null&&count<=5?.3:0;
        candidates.add(new WalkCandidate("osm",point.id().replace('/','-'),point.label(),point.lon(),point.lat(),opening,status,count,true,"",List.of(),1+bonus+hoursBonus(status),wikidata,""));
      }
      for(var point:wikiCandidates){
        boolean present=crossCheck==null||crossCheck.contains(Long.parseLong(point.wikimapiaId()));
        boolean hasLicensedPhoto=point.photos().stream().anyMatch(p->!p.license().isBlank()&&!p.license().equals("unknown"));
        candidates.add(new WalkCandidate(point.source(),point.id(),point.label(),point.lon(),point.lat(),point.openingHours(),point.visitStatus(),null,present,point.description(),point.photos(),point.score()+(present?0:.5)+(hasLicensedPhoto?.1:0),"",point.wikimapiaId()));
      }

      var deduplicated=new LinkedHashMap<String,WalkCandidate>();
      for(var point:candidates){
        String identity=Math.round(point.lon()*100000)+":"+Math.round(point.lat()*100000)+":"+point.label().toLowerCase(Locale.ROOT);
        var previous=deduplicated.get(identity);
        if(previous==null)deduplicated.put(identity,point);
        else if(point.source().equals("wikimapia"))deduplicated.put(identity,new WalkCandidate(point.source(),point.id(),point.label(),point.lon(),point.lat(),previous.openingHours(),previous.visitStatus(),previous.wikidataSitelinks(),true,point.description(),point.photos(),point.score()-(point.inOsm()?0:.5)+hoursBonus(previous.visitStatus()),previous.wikidataId(),point.wikimapiaId()));
      }

      var result=deduplicated.values().stream()
              .sorted(Comparator.comparingDouble(WalkCandidate::score).reversed().thenComparingDouble(p->WikimapiaPoiSourceImpl.distance(request.startLon(),request.startLat(),p.lon(),p.lat())))
              .limit(request.maxStops()).toList();
      cache.put(key,result);
      log.info("walk.search theme={} lang={} osm_raw={} wiki_raw={} wiki_enriched={} result={} elapsed_ms={}",
              request.theme(),request.language(),osmPoints.size(),wikiPoints.size(),wikiCandidates.size(),result.size(),(System.nanoTime()-started)/1_000_000);
      return result;
    } finally {
      if(osmTask!=null&&!osmTask.isDone())osmTask.cancel(true);
      if(wikiTask!=null&&!wikiTask.isDone())wikiTask.cancel(true);
      permit.release();
    }
  }

  private static double preScore(WikimapiaPoi point,WalkThemeProperties.Theme theme){return (WikimapiaPoiSourceImpl.matches(point.categories(),theme.wikimapiaCategories())?2:0)+(WikimapiaPoiSourceImpl.matches(List.of(point.name()),theme.descriptionKeywords())?1:0);}
  private static double hoursBonus(String status){return "OPEN".equals(status)?.2:"CLOSED".equals(status)?-.2:0;}
  private static <T> List<T> await(Future<List<T>> future,long deadline,long timeout){
    try{long remaining=Math.max(1,Math.min(timeout,(deadline-System.nanoTime())/1_000_000));return future.get(remaining,TimeUnit.MILLISECONDS);}
    catch(InterruptedException interrupted){Thread.currentThread().interrupt();future.cancel(true);return List.of();}
    catch(ExecutionException|TimeoutException unavailable){future.cancel(true);return List.of();}
  }
  @PreDestroy void close(){discovery.shutdownNow();}
}
