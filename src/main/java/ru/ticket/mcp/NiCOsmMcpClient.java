package ru.ticket.mcp;

import com.fasterxml.jackson.databind.*;
import io.modelcontextprotocol.client.McpSyncClient;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** ni-c/osm-mcp's actual near/category/radius_m contract, not an invented tools schema. */
@Component
public class NiCOsmMcpClient implements OsmMcpClient {
  private final McpToolSession session;

  public NiCOsmMcpClient(@Qualifier("osmMcpSyncClient") ObjectProvider<McpSyncClient> client,ObjectMapper mapper){
    session=new McpToolSession(client::getIfAvailable,mapper,Set.of("find_nearby_pois","poi_details"));
  }

  public boolean enabled(){return session.enabled();}

  public List<Poi> findNearby(double lon,double lat,int radius,List<String> tags,int limit) throws IOException {
    var result=new LinkedHashMap<String,Poi>();
    for(var tag:tags){
      var payload=session.call("find_nearby_pois",Map.of(
              "near",lat+","+lon,
              "category",tag.endsWith("=*")?tag.substring(0,tag.length()-2):tag,
              "radius_m",radius,
              "limit",Math.min(25,limit)));
      for(var value:payload.path("results")){var poi=parse(value);if(poi!=null)result.putIfAbsent(poi.id(),poi);}
      if(result.size()>=limit)break;
    }
    return result.values().stream().limit(limit).toList();
  }

  public PoiDetails poiDetails(String id) throws IOException {
    if(!id.matches("(node|way|relation)/[0-9]{1,20}"))throw new IOException("Invalid OSM id");
    var value=session.call("poi_details",Map.of("osm_id",id));
    var poi=parse(value);if(poi==null)throw new IOException("Invalid OSM details");
    return new PoiDetails(poi,poi.tags().getOrDefault("opening_hours",""),poi.tags().getOrDefault("wikidata",""),null);
  }

  static Poi parse(JsonNode value){
    String id=value.path("osm").asText(value.path("id").asText(""));
    String label=value.path("name").asText("");
    double lon=value.path("lon").asDouble(Double.NaN),lat=value.path("lat").asDouble(Double.NaN);
    if(!id.matches("(node|way|relation)/[0-9]{1,20}")||label.isBlank()||label.length()>250
            ||!Double.isFinite(lon)||!Double.isFinite(lat)||Math.abs(lon)>=180||Math.abs(lat)>85)return null;

    var tags=new LinkedHashMap<String,String>();

    // 1. Все текстовые поля верхнего уровня (как было)
    value.fields().forEachRemaining(entry->{
      if(entry.getValue().isTextual()&&entry.getValue().asText().length()<=500)
        tags.put(entry.getKey(),entry.getValue().asText());
    });

    // 2. Явный объект tags (как было)
    value.path("tags").fields().forEachRemaining(entry->{
      if(entry.getValue().isTextual()&&entry.getValue().asText().length()<=500)
        tags.put(entry.getKey(),entry.getValue().asText());
    });

    // 3. НОВОЕ: разворачиваем category="amenity=bar" → {amenity: bar}
    expandCategory(tags, tags.get("category"));

    // 4. НОВОЕ: массив categories, если MCP отдаёт массив
    value.path("categories").forEach(cat -> expandCategory(tags, cat.asText("")));

    // 5. НОВОЕ: иногда MCP отдаёт теги внутри "properties"
    value.path("properties").fields().forEachRemaining(entry->{
      if(entry.getValue().isTextual()&&entry.getValue().asText().length()<=500)
        tags.putIfAbsent(entry.getKey(),entry.getValue().asText());
    });

    return new Poi(id,label,lon,lat,Map.copyOf(tags));
  }

  private static void expandCategory(Map<String,String> tags,String category){
    if(category==null||!category.contains("="))return;
    String[] parts=category.split("=",2);
    if(parts.length==2&&!parts[0].isBlank()&&!parts[1].isBlank())
      tags.putIfAbsent(parts[0],parts[1]);
  }
}