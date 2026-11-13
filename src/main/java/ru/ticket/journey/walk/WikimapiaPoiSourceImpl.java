package ru.ticket.journey.walk;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.ticket.journey.*;
import static ru.ticket.journey.walk.WalkRemote.encode;

/** Uncategorised box scans preserve community places that a tourist-category query would omit. */
@Component
public class WikimapiaPoiSourceImpl implements WikimapiaPoiSource {
  private final WikimapiaKeyProvider keys;
  private final WikimapiaRateLimiter limiter;
  private final WalkRemote remote;
  private final OsmPlaceService osm;
  private final String endpoint;
  private final int pageSize;
  private final WalkCache<String,List<WikimapiaPoi>> boxes=new WalkCache<>(64,Duration.ofMinutes(30));
  private final WalkCache<String,WikimapiaPoiDetails> details=new WalkCache<>(128,Duration.ofMinutes(30));
  private final WalkCache<String,Boolean> excludedAreas=new WalkCache<>(128,Duration.ofMinutes(30));
  private final WalkCache<String,Set<Long>> checks=new WalkCache<>(128,Duration.ofHours(24));
  public WikimapiaPoiSourceImpl(WikimapiaKeyProvider keys,WikimapiaRateLimiter limiter,WalkRemote remote,OsmPlaceService osm,
      @Value("${app.journeys.wikimapia-url:https://api.wikimapia.org/}") String endpoint,
      @Value("${app.journeys.wikimapia.box-page-size:100}") int pageSize){
    this.keys=keys;this.limiter=limiter;this.remote=remote;this.osm=osm;this.endpoint=endpoint;this.pageSize=Math.max(1,Math.min(100,pageSize));
  }
  public boolean enabled(){return keys.enabled();}
  public List<WikimapiaPoi> fetchInBox(double x1,double y1,double x2,double y2,WalkThemeProperties.Theme theme,String language){
    if(!enabled())return List.of();
    String locale=locale(language),box=x1+","+y1+","+x2+","+y2,key=box+":"+locale;
    var found=boxes.get(key);if(found!=null)return filter(found,theme);
    var all=new LinkedHashMap<Long,WikimapiaPoi>();long deadline=System.nanoTime()+Duration.ofSeconds(7).toNanos();
    for(int page=1;page<=100&&System.nanoTime()<deadline&&!Thread.currentThread().isInterrupted();page++){
      try(var lease=limiter.acquire(false)){
        var payload=remote.get(endpoint+"?function=box&format=json&count="+pageSize+"&page="+page+"&language="+locale+"&bbox="+box+"&key="+encode(keys.key()));
        var items=payload.path("folder");if(!items.isArray())break;
        for(var item:items){var point=point(item);if(point!=null&&point.lon()>=x1&&point.lon()<=x2&&point.lat()>=y1&&point.lat()<=y2)all.put(point.id(),point);}
        if(items.size()<pageSize||limiter.reserveDetailsAfterBoxPage())break;
      }catch(IOException unavailable){break;}
    }
    var value=List.copyOf(all.values());if(!value.isEmpty())boxes.put(key,value);
    return filter(value,theme);
  }
  private static List<WikimapiaPoi> filter(List<WikimapiaPoi> values,WalkThemeProperties.Theme theme){
    // Missing categories must reach detail enrichment: this is the community layer's key purpose.
    return values.stream().filter(point->point.categories().isEmpty()||point.hasPhotos()
        ||matches(point.categories(),theme.wikimapiaCategories())||matches(List.of(point.name()),theme.descriptionKeywords())).toList();
  }
  public WikimapiaPoiDetails enrich(long id,String language){
    if(!enabled()||id<=0)return null;
    String locale=locale(language),key=id+":"+locale;var cached=details.get(key);if(cached!=null)return cached;
    if(Boolean.TRUE.equals(excludedAreas.get(key)))return null;
    try(var lease=limiter.acquire(true)){
      // All is documented by Wikimapia; providers rejecting a block fail softly, never exposing the key.
      var value=remote.get(endpoint+"?function=place.getbyid&format=json&data_blocks=main,photos,location&id="+id+"&language="+locale+"&key="+encode(keys.key()));
      var point=point(value);if(point==null)return null;
      if (isLargeArea(value.path("box").asText(""))) {
        excludedAreas.put(key, true);
        return null;
      }
      String description=Jsoup.parse(value.path("description").asText("")).text();if(description.length()>1500)description=description.substring(0,1500);
      var photos=new ArrayList<WikimapiaPhoto>();
      for(var photo:value.path("photos")){
        if(photos.size()==3)break;
        String license=photo.path("license").asText("");if(license.isBlank())license="unknown";
        photos.add(new WikimapiaPhoto(photo.path("url").asText(photo.path("big_url").asText("")),photo.path("thumbnail").asText(photo.path("thumbnail_url").asText(photo.path("thumb_url").asText(""))),Jsoup.parse(photo.path("author").asText(photo.path("user_name").asText(""))).text(),license));
      }
      var result=new WikimapiaPoiDetails(point,description,point.categories(),List.copyOf(photos));details.put(key,result);return result;
    }catch(IOException unavailable){return null;}
  }
  public Set<Long> crossCheckOsm(List<WikimapiaPoi> points){
    if(points.isEmpty())return Set.of();
    String key=points.stream().map(point->Long.toString(point.id())).sorted().reduce((a,b)->a+","+b).orElse("");
    var cached=checks.get(key);if(cached!=null)return cached;
    StringBuilder query=new StringBuilder("[out:json][timeout:4][maxsize:1000000];(");
    for(var point:points.stream().limit(25).toList())query.append("nwr(around:15,").append(point.lat()).append(',').append(point.lon()).append(");");
    query.append(");out center 500;");
    try{
      var payload=osm.walkQuery(query.toString());var elements=payload.path("elements");if(!elements.isArray()||payload.has("remark"))return null;
      var present=new HashSet<Long>();
      for(var point:points)for(var element:elements){
        var center=element.has("center")?element.path("center"):element;
        double lon=center.path("lon").asDouble(Double.NaN),lat=center.path("lat").asDouble(Double.NaN);
        if(distance(point.lon(),point.lat(),lon,lat)<=15){present.add(point.id());break;}
      }
      var result=Set.copyOf(present);checks.put(key,result);return result;
    }catch(IOException unavailable){return null;}
  }
  static WikimapiaPoi point(JsonNode item){
    long id=item.path("id").asLong(0);String name=Jsoup.parse(item.path("name").asText(item.path("title").asText(""))).text();
    var location=item.path("location");double x=location.path("lon").asDouble(Double.NaN),y=location.path("lat").asDouble(Double.NaN);
    if(id<=0||name.isBlank()||name.length()>250||!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>=180||Math.abs(y)>85)return null;
    var categories=new ArrayList<String>();for(var category:item.has("categories")?item.path("categories"):item.path("tags")){String title=category.isTextual()?category.asText():category.path("title").asText(category.path("name").asText(""));if(!title.isBlank()&&title.length()<=150)categories.add(title);}
    return new WikimapiaPoi(id,name,x,y,List.copyOf(categories),item.path("photos_count").asInt(item.path("photo_count").asInt(0))>0||!item.path("photos").isEmpty());
  }
  static boolean matches(List<String> values,List<String> patterns){
    return values.stream().anyMatch(value->patterns.stream().anyMatch(pattern->{
      String lower=value.toLowerCase(Locale.ROOT),prefix=pattern.toLowerCase(Locale.ROOT);
      if(prefix.contains(" "))return lower.contains(prefix);
      return Arrays.stream(lower.split("[^\\p{L}\\p{N}_]+",-1)).anyMatch(word->word.startsWith(prefix));
    }));
  }
  /** A district centroid is not an entrance or a useful walking stop. Unknown bounds remain eligible. */
  static boolean isLargeArea(String box) {
    String[] bounds = box.split(",", -1);
    if (bounds.length != 4) return false;
    try {
      double south = Double.parseDouble(bounds[0].trim());
      double west = Double.parseDouble(bounds[1].trim());
      double north = Double.parseDouble(bounds[2].trim());
      double east = Double.parseDouble(bounds[3].trim());
      if (!Double.isFinite(south) || !Double.isFinite(west) || !Double.isFinite(north)
          || !Double.isFinite(east) || Math.abs(south) > 90 || Math.abs(north) > 90
          || Math.abs(west) > 180 || Math.abs(east) > 180) return false;
      return distance(west, south, east, north) > 300;
    } catch (NumberFormatException malformed) {
      return false;
    }
  }
  public static double distance(double x1,double y1,double x2,double y2){return Math.hypot((x1-x2)*Math.cos(Math.toRadians((y1+y2)/2)),y1-y2)*111195;}
  private static String locale(String value){return Set.of("en","ru","de","fr","it","es","nl","pl","zh").contains(value)?value:"en";}
}
