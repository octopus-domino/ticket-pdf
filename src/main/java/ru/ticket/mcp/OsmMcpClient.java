package ru.ticket.mcp;

import java.io.IOException;
import java.util.*;

public interface OsmMcpClient {
  record Poi(String id,String label,double lon,double lat,Map<String,String> tags) {}
  record PoiDetails(Poi poi,String openingHours,String wikidataId,Integer wikidataSitelinks) {}
  boolean enabled();
  List<Poi> findNearby(double lon,double lat,int radiusMeters,List<String> tags,int limit) throws IOException;
  PoiDetails poiDetails(String id) throws IOException;
}
