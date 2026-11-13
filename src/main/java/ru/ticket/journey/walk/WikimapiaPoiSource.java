package ru.ticket.journey.walk;

import java.util.*;

public interface WikimapiaPoiSource {
  record WikimapiaPoi(long id,String name,double lon,double lat,List<String> categories,boolean hasPhotos) {}
  record WikimapiaPhoto(String url,String thumbUrl,String author,String license) {}
  record WikimapiaPoiDetails(WikimapiaPoi poi,String description,List<String> categoryNames,List<WikimapiaPhoto> photos) {}
  boolean enabled();
  List<WikimapiaPoi> fetchInBox(double lonMin,double latMin,double lonMax,double latMax,WalkThemeProperties.Theme theme,String language);
  default List<WikimapiaPoi> fetchInBox(double lonMin,double latMin,double lonMax,double latMax,WalkThemeProperties.Theme theme){return fetchInBox(lonMin,latMin,lonMax,latMax,theme,"en");}
  WikimapiaPoiDetails enrich(long id,String language);
  default WikimapiaPoiDetails enrich(long id){return enrich(id,"en");}
  /** Null means the OSM cross-check failed; it must not be presented as proven absence. */
  Set<Long> crossCheckOsm(List<WikimapiaPoi> points);
}
