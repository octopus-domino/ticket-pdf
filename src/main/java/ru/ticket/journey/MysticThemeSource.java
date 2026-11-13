package ru.ticket.journey;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.journey.walk.WalkThemeService.WalkCandidate;

@Service
public class MysticThemeSource {
  private final MysticPlaceRepository repository;
  private final boolean postgis;
  public MysticThemeSource(MysticPlaceRepository repository,@Value("${spring.datasource.url:}") String url){
    this.repository=repository;this.postgis=url.startsWith("jdbc:postgresql:");
  }
  @Transactional(readOnly=true)
  public List<WalkCandidate> findCandidates(double lon,double lat,int maxStops,double radiusKm){
    if(!Double.isFinite(lon)||Math.abs(lon)>=180||!Double.isFinite(lat)||Math.abs(lat)>85
        ||!Double.isFinite(radiusKm)||radiusKm<.1||radiusKm>3||maxStops<1||maxStops>12)
      throw new IllegalArgumentException("Invalid mystic search bounds");
    List<MysticPlace> found;
    int window=Math.min(144,maxStops*12);
    if(postgis)found=repository.findNearby(lon,lat,radiusKm*1000,window);
    else {
      double dy=radiusKm/111.195,dx=dy/Math.cos(Math.toRadians(lat));
      var rows=new ArrayList<MysticPlace>();
      if(lon-dx< -180){rows.addAll(repository.findInBox(lat-dy,lat+dy,-180,lon+dx));rows.addAll(repository.findInBox(lat-dy,lat+dy,lon-dx+360,180));}
      else if(lon+dx>180){rows.addAll(repository.findInBox(lat-dy,lat+dy,lon-dx,180));rows.addAll(repository.findInBox(lat-dy,lat+dy,-180,lon+dx-360));}
      else rows.addAll(repository.findInBox(lat-dy,lat+dy,lon-dx,lon+dx));
      found=rows.stream().filter(p->distance(lon,lat,p)<=radiusKm*1000)
          .sorted(Comparator.comparingDouble((MysticPlace p)->distance(lon,lat,p)).thenComparing(MysticPlace::getId)).limit(window).toList();
    }
    // Keep distinct legends in storage, but do not route repeatedly to aliases of the same coordinate.
    var coordinates=new HashSet<String>();
    return found.stream().filter(p->coordinates.add(Math.round(p.getLongitude()*100000)+":"+Math.round(p.getLatitude()*100000)))
        .limit(maxStops).map(p->new WalkCandidate("mystic","mystic-"+p.getId(),p.getName(),p.getLongitude(),p.getLatitude(),
        "","UNKNOWN",null,true,Objects.requireNonNullElse(p.getDescription(),""),List.of(),10.0,"","",Objects.requireNonNullElse(p.getSourceUrl(),""))).toList();
  }
  private static double distance(double lon,double lat,MysticPlace p){
    double a=Math.pow(Math.sin(Math.toRadians(p.getLatitude()-lat)/2),2)
        +Math.cos(Math.toRadians(lat))*Math.cos(Math.toRadians(p.getLatitude()))*Math.pow(Math.sin(Math.toRadians(p.getLongitude()-lon)/2),2);
    return 6371008.8*2*Math.asin(Math.sqrt(Math.min(1,a)));
  }
}
