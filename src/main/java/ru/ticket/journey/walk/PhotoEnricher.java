package ru.ticket.journey.walk;

import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;
import ru.ticket.journey.*;
import ru.ticket.mcp.CommonsMcpClient;
import ru.ticket.mcp.CommonsMcpClient.CommonsImage;

/** Separate, explicitly requested enrichment. A missing image never removes a walking stop. */
@Service
public class PhotoEnricher {
  private final CommonsMcpClient commons;
  private final WalkRemote remote;
  private final WikimapiaPoiSource wikimapia;
  private final JourneyMediaService media;
  private final WalkCache<String,Optional<CommonsImage>> cache=new WalkCache<>(128,Duration.ofMinutes(30));
  public PhotoEnricher(CommonsMcpClient commons,WalkRemote remote,WikimapiaPoiSource wikimapia,JourneyMediaService media){this.commons=commons;this.remote=remote;this.wikimapia=wikimapia;this.media=media;}
  public List<JourneyRouteService.Photo> enrich(List<JourneyRouteService.Stop> stops,String language){
    if(stops==null||stops.isEmpty()||stops.size()>12)throw ApiException.badRequest("journey.route.invalid-route");
    var ids = new HashSet<String>();
    for(var stop:stops){
      if(stop==null||stop.id()==null||!stop.id().matches("[a-zA-Z0-9_-]{1,64}")||stop.label()==null||stop.label().length()>300||!Double.isFinite(stop.longitude())||!Double.isFinite(stop.latitude())||Math.abs(stop.longitude())>=180||Math.abs(stop.latitude())>85)throw ApiException.badRequest("journey.route.invalid-route");
      JourneyRouteService.validateMeta(stop.meta());
      if(!ids.add(stop.id()))throw ApiException.badRequest("journey.route.invalid-route");
    }
    long deadline=System.nanoTime()+Duration.ofSeconds(18).toNanos();var result=new ArrayList<JourneyRouteService.Photo>();
    try(var budget=WalkBudget.until(deadline)){
      for(var stop:stops){
        if(System.nanoTime()>deadline||Thread.currentThread().isInterrupted())break;
        try{
          var image=find(stop,language);if(image==null)continue;
          String data=download(image.imageUrl());var asset=media.save(data);
          result.add(new JourneyRouteService.Photo(stop.id(),asset.id(),image.pageUrl(),image.author(),image.license(),image.licenseUrl()));
        }catch(IOException|ApiException unavailable){/* Preserve the candidate without a photo. */}
      }
    }
    return List.copyOf(result);
  }
  CommonsImage find(JourneyRouteService.Stop stop,String language){
    String key=stop.longitude()+":"+stop.latitude()+":"+stop.label();var stored=cache.get(key);if(stored!=null)return stored.orElse(null);
    if(commons.enabled())try{
      var found=commons.searchByName(stop.label(),stop.longitude(),stop.latitude(),500,1);
      for(var image:found)if(CommonsImages.validImage(image.imageUrl())&&CommonsImages.validLicense(image.licenseUrl())&&!image.author().isBlank()){
        cache.put(key,Optional.of(image));return image;
      }
    }catch(IOException unavailable){/* Direct Commons is independent of the gateway. */}
    try{
      var found=remote.get("https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2&generator=geosearch&ggsnamespace=6&ggscoord="
          +WalkRemote.encode(stop.latitude()+"|"+stop.longitude())+"&ggsradius=500&ggslimit=3&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=500");
      var images=CommonsImages.parse(found);if(!images.isEmpty()){cache.put(key,Optional.of(images.get(0)));return images.get(0);}
    }catch(IOException unavailable){/* Last source: only explicitly licensed Wikimapia photos. */}
    String id=stop.meta()==null?"":stop.meta().getOrDefault("wikimapia_id","");
    if(id.matches("[1-9][0-9]{0,18}")&&wikimapia.enabled()){
      var detail=wikimapia.enrich(Long.parseLong(id),language);
      if(detail!=null)for(var photo:detail.photos()){
        String license=licenseUrl(photo.license());if(license==null||photo.author().isBlank())continue;
        String image=photo.thumbUrl().isBlank()?photo.url():photo.thumbUrl();
        if(!wikimapiaImage(image))continue;
        var result=new CommonsImage(image,"https://wikimapia.org/"+id,photo.author(),photo.license(),license);cache.put(key,Optional.of(result));return result;
      }
    }
    cache.put(key,Optional.empty());return null;
  }
  String download(String url) throws IOException {
    if(!CommonsImages.validImage(url)&&!wikimapiaImage(url))throw new IOException("Invalid photo source");
    var connection=(HttpURLConnection)URI.create(url).toURL().openConnection();
    try{
      connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(WalkBudget.timeout(2000));connection.setReadTimeout(WalkBudget.timeout(5000));
      connection.setRequestProperty("User-Agent","CoDoc/1.0 (https://codoc.site; attributed walking photographs)");
      if(connection.getResponseCode()!=200)throw new IOException("Photo unavailable");
      byte[] bytes;try(var stream=connection.getInputStream()){bytes=stream.readNBytes(900001);}
      if(bytes.length>900000||bytes.length<4)throw new IOException("Invalid photo size");
      String mime=(bytes[0]&255)==137&&(bytes[1]&255)==80?"image/png":(bytes[0]&255)==255&&(bytes[1]&255)==216?"image/jpeg":null;
      if(mime==null)throw new IOException("Unsupported photo format");
      return "data:"+mime+";base64,"+Base64.getEncoder().encodeToString(bytes);
    }finally{connection.disconnect();}
  }
  static String licenseUrl(String value){
    String normalized=value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]","");
    if(Set.of("cc0","cc01.0").contains(normalized))return "https://creativecommons.org/publicdomain/zero/1.0/";
    if(normalized.equals("publicdomain"))return "https://creativecommons.org/publicdomain/mark/1.0/";
    var match=java.util.regex.Pattern.compile("cc(by|bysa)([1-4]\\.0)").matcher(normalized);
    return match.matches()?"https://creativecommons.org/licenses/"+(match.group(1).equals("bysa")?"by-sa":"by")+"/"+match.group(2)+"/":null;
  }
  private static boolean wikimapiaImage(String url){
    try{var uri=URI.create(url);return "https".equals(uri.getScheme())&&uri.getHost()!=null&&uri.getHost().matches("photos[0-9]{0,2}\\.wikimapia\\.org")&&uri.getPort()==-1&&uri.getUserInfo()==null&&uri.getRawPath().matches("/p/[a-zA-Z0-9/_-]+\\.(jpg|jpeg|png)");}
    catch(RuntimeException invalid){return false;}
  }
}
