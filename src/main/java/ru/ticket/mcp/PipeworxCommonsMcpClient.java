package ru.ticket.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.ticket.journey.walk.CommonsImages;

/** Pipeworx prefixes search in its live tools/list, while file_info keeps its original name. */
@Component
public class PipeworxCommonsMcpClient implements CommonsMcpClient {
  private final McpToolSession session;
  public PipeworxCommonsMcpClient(@Qualifier("commonsMcpSyncClient") ObjectProvider<McpSyncClient> client,ObjectMapper mapper){
    session=new McpToolSession(client::getIfAvailable,mapper,Set.of("commons_wikimedia_search","file_info"));
  }
  public boolean enabled(){return session.enabled();}
  public List<CommonsImage> searchNearby(double lon,double lat,int radius,int limit) throws IOException {
    return searchByName("",lon,lat,radius,limit);
  }
  public List<CommonsImage> searchByName(String name,double lon,double lat,int radius,int limit) throws IOException {
    String query=(name==null?"":name.replaceAll("[\\p{Cntrl}\\\"{}]"," ").strip())+" nearcoord:"+Math.max(50,Math.min(1000,radius))+"m,"+lat+","+lon;
    var found=session.call("commons_wikimedia_search",Map.of("query",query.strip(),"namespace",6,"limit",Math.min(3,Math.max(1,limit))));
    var result=new ArrayList<CommonsImage>();
    for(var item:found.path("query").path("search")){
      String title=item.path("title").asText("");if(!title.startsWith("File:")||title.length()>500)continue;
      result.addAll(CommonsImages.parse(session.call("file_info",Map.of("title",title))));
      if(result.size()>=limit)break;
    }
    return result.stream().limit(limit).toList();
  }
}
