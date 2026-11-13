package ru.ticket.mcp;

import java.io.IOException;
import java.util.List;

public interface CommonsMcpClient {
  record CommonsImage(String imageUrl,String pageUrl,String author,String license,String licenseUrl) {}
  boolean enabled();
  List<CommonsImage> searchNearby(double lon,double lat,int radiusMeters,int limit) throws IOException;
  default List<CommonsImage> searchByName(String name,double lon,double lat,int radiusMeters,int limit) throws IOException {
    return searchNearby(lon,lat,radiusMeters,limit);
  }
}
