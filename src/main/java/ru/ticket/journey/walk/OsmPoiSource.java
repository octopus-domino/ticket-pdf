package ru.ticket.journey.walk;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import ru.ticket.journey.OsmPlaceService;
import ru.ticket.mcp.OsmMcpClient;

@Component
public class OsmPoiSource {
  private final OsmMcpClient mcp;
  private final OsmPlaceService osm;
  private final WalkCache<String,List<OsmMcpClient.Poi>> cache=new WalkCache<>(64,Duration.ofMinutes(10));
  private final ExecutorService mcpCalls=new ThreadPoolExecutor(
          1,1,0,TimeUnit.MILLISECONDS,new SynchronousQueue<>(),
          task->{var thread=new Thread(task,"walk-osm-mcp");thread.setDaemon(true);return thread;},
          new ThreadPoolExecutor.AbortPolicy());

  public OsmPoiSource(OsmMcpClient mcp,OsmPlaceService osm){this.mcp=mcp;this.osm=osm;}

  public List<OsmMcpClient.Poi> search(double lon,double lat,int radius,WalkThemeProperties.Theme theme,String language) throws IOException {
    String key=String.format(Locale.ROOT,"%.4f:%.4f:%d:%s:%s",lon,lat,radius,theme.tags(),language);
    var cached=cache.get(key);if(cached!=null)return cached;

    // 1. MCP (primary)
    long mcpDeadline=System.nanoTime()+Duration.ofSeconds(3).toNanos();
    if(mcp.enabled())try{
      var points=mcpWithin(()->mcp.findNearby(lon,lat,radius,theme.tags(),25),mcpDeadline);
      if(!points.isEmpty()){
        var complete=new ArrayList<OsmMcpClient.Poi>();
        for(var point:points){
          if(Thread.currentThread().isInterrupted())break;
          try{
            var detail=mcpWithin(()->mcp.poiDetails(point.id()),mcpDeadline);
            complete.add(detail.poi());
          }catch(IOException unavailable){
            var tags=new LinkedHashMap<>(point.tags());
            tags.put("codoc:details_verified","false");
            complete.add(new OsmMcpClient.Poi(point.id(),point.label(),point.lon(),point.lat(),Map.copyOf(tags)));
          }
        }
        cache.put(key,List.copyOf(complete));
        return List.copyOf(complete);
      }
    }catch(IOException unavailable){/* fall through to direct Overpass */}

    if(Thread.currentThread().isInterrupted())return List.of();

    // 2. Direct Overpass (fallback)
    // ФИКС: [timeout:15][maxsize:16777216] вместо [timeout:4][maxsize:1000000]
    // В центре Москвы 4-5 тегов × ~20 элементов с тегами легко дают >1 МБ,
    // что вызывало "remark: runtime error: Query timed out" на overpass.osm.ch
    var query=new StringBuilder("[out:json][timeout:15][maxsize:16777216];(");
    for(String tag:theme.tags()){
      if(!tag.matches("[a-z_:]{1,40}=[a-z_0-9*]{1,60}"))throw new IOException("Invalid theme configuration");
      var pair=tag.split("=",2);
      query.append("nwr[\"").append(pair[0]).append('"');
      if(!pair[1].equals("*"))query.append("=\"").append(pair[1]).append('"');
      query.append("](around:").append(radius).append(',').append(lat).append(',').append(lon).append(");");
    }
    query.append(");out tags center 80;");

    JsonNode payload=osm.walkQuery(query.toString());
    if(!payload.path("elements").isArray()||payload.has("remark")){
      throw new IOException("OSM unavailable: "+payload.path("remark").asText("no elements"));
    }

    var result=new ArrayList<OsmMcpClient.Poi>();
    for(var element:payload.path("elements")){
      var point=element.has("center")?element.path("center"):element;
      var tags=element.path("tags");
      String id=element.path("type").asText()+"/"+element.path("id").asText();
      String name=tags.path("name:"+language).asText(tags.path("name").asText(""));
      double x=point.path("lon").asDouble(Double.NaN),y=point.path("lat").asDouble(Double.NaN);
      if(!id.matches("(node|way|relation)/[0-9]{1,20}")||name.isBlank()||name.length()>250
              ||!Double.isFinite(x)||!Double.isFinite(y)
              ||WikimapiaPoiSourceImpl.distance(lon,lat,x,y)>radius)continue;
      var values=new LinkedHashMap<String,String>();
      tags.fields().forEachRemaining(entry->{
        if(entry.getValue().isTextual()&&entry.getKey().length()<=80&&entry.getValue().asText().length()<=500)
          values.put(entry.getKey(),entry.getValue().asText());
      });
      result.add(new OsmMcpClient.Poi(id,name,x,y,Map.copyOf(values)));
    }
    var points=List.copyOf(result);
    if(!points.isEmpty())cache.put(key,points);
    return points;
  }

  private <T> T mcpWithin(Callable<T> call,long deadline) throws IOException {
    long remaining=(deadline-System.nanoTime())/1_000_000;
    if(remaining<=0)throw new IOException("MCP discovery budget exhausted");
    Future<T> task;
    try{task=mcpCalls.submit(call);}catch(RejectedExecutionException busy){throw new IOException("MCP discovery busy");}
    try{return task.get(remaining,TimeUnit.MILLISECONDS);}
    catch(InterruptedException interrupted){task.cancel(true);Thread.currentThread().interrupt();throw new IOException("MCP discovery interrupted");}
    catch(TimeoutException|ExecutionException unavailable){task.cancel(true);throw new IOException("MCP discovery unavailable");}
  }

  @PreDestroy void close(){mcpCalls.shutdownNow();}

  public static boolean matches(Map<String,String> tags,WalkThemeProperties.Theme theme){
    // 1. Стандартная проверка по тегам темы
    boolean direct=theme.tags().stream().anyMatch(selector->{
      var pair=selector.split("=",2);
      return pair.length==2&&tags.containsKey(pair[0])
              &&(pair[1].equals("*")||pair[1].equals(tags.get(pair[0])));
    });
    if(direct)return true;

    // 2. ФИКС: для темы "bars" — amenity=cafe + cuisine=bar|beer|wine|pub тоже бар
    // В России многие бары в OSM затегированы как cafe с cuisine=bar
    if(theme.tags().contains("amenity=bar")&&"cafe".equals(tags.get("amenity"))){
      String cuisine=tags.getOrDefault("cuisine","").toLowerCase(Locale.ROOT);
      if(cuisine.contains("bar")||cuisine.contains("beer")||cuisine.contains("wine")||cuisine.contains("pub"))
        return true;
    }

    return false;
  }
}
