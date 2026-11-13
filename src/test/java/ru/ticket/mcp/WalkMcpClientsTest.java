package ru.ticket.mcp;

import com.fasterxml.jackson.databind.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.*;

class WalkMcpClientsTest {
  @Test void actualSdkPerformsHttpHandshakeAndOnlyCallsAllowlistedTools() throws Exception {
    var mapper=new ObjectMapper();var called=new ArrayList<String>();
    var rpc=new ResponseDefinitionTransformerV2(){
      public String getName(){return "test-mcp";}
      public ResponseDefinition transform(ServeEvent event){
        try{
          var request=mapper.readTree(event.getRequest().getBodyAsString());String method=request.path("method").asText();
          if(method.startsWith("notifications/"))return aResponse().withStatus(202).build();
          var result=mapper.createObjectNode();
          if(method.equals("initialize")){
            result.put("protocolVersion","2025-03-26");result.putObject("capabilities").putObject("tools");result.putObject("serverInfo").put("name","fixture").put("version","1");
          }else if(method.equals("tools/call")){
            String tool=request.path("params").path("name").asText();called.add(tool);result.putArray("content");
            var structured=result.putObject("structuredContent");
            if(tool.equals("find_nearby_pois")){
              var point=structured.putArray("results").addObject().put("osm","node/1").put("name","Fixture pub").put("lon",37.62).put("lat",55.75).put("amenity","pub");
              point.put("opening_hours","24/7");
            }else if(tool.equals("poi_details")){
              structured.put("osm","node/1").put("name","Fixture pub").put("lon",37.62).put("lat",55.75);
              structured.putObject("tags").put("amenity","pub").put("opening_hours","24/7").put("wikidata","Q1");
            }else if(tool.equals("commons_wikimedia_search"))structured.putObject("query").putArray("search").addObject().put("title","File:Fixture.jpg");
            else if(tool.equals("file_info")){
              var info=structured.putObject("query").putArray("pages").addObject().putArray("imageinfo").addObject();
              info.put("url","https://upload.wikimedia.org/wikipedia/commons/a/ab/Fixture.jpg").put("descriptionurl","https://commons.wikimedia.org/wiki/File:Fixture.jpg");
              var meta=info.putObject("extmetadata");meta.putObject("Artist").put("value","Fixture photographer");meta.putObject("LicenseShortName").put("value","CC BY 4.0");meta.putObject("LicenseUrl").put("value","https://creativecommons.org/licenses/by/4.0/");
            }
          }
          var response=mapper.createObjectNode().put("jsonrpc","2.0");response.set("id",request.get("id"));response.set("result",result);
          return aResponse().withHeader("Content-Type","application/json").withBody(response.toString()).build();
        }catch(Exception error){return aResponse().withStatus(500).build();}
      }
    };
    var server=new WireMockServer(options().dynamicPort().extensions(rpc));server.start();
    server.stubFor(post(urlEqualTo("/mcp")).willReturn(aResponse().withTransformers("test-mcp")));
    try(var client=McpClientFactory.create(mapper,server.baseUrl()+"/mcp")){
      var session=new McpToolSession(()->client,mapper,Set.of("find_nearby_pois","poi_details","commons_wikimedia_search","file_info"));
      assertEquals(1,session.call("find_nearby_pois",Map.of("near","55.75,37.62","category","amenity=pub")).path("results").size());
      assertEquals("Q1",session.call("poi_details",Map.of("osm_id","node/1")).path("tags").path("wikidata").asText());
      var image=ru.ticket.journey.walk.CommonsImages.parse(session.call("file_info",Map.of("title","File:Fixture.jpg"))).get(0);
      assertEquals("CC BY 4.0",image.license());assertTrue(image.imageUrl().contains("/thumb/"));
      assertThrows(java.io.IOException.class,()->session.call("remember",Map.of()));
      assertEquals(List.of("find_nearby_pois","poi_details","file_info"),called);
      server.verify(1,postRequestedFor(urlEqualTo("/mcp")).withRequestBody(matchingJsonPath("$.method",equalTo("initialize"))));
    }finally{server.stop();}
  }
}
