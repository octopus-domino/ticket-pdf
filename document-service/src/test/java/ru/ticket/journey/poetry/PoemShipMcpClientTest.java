package ru.ticket.journey.poetry;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoemShipMcpClientTest {
  private final ObjectMapper mapper=new ObjectMapper();
  @Test void initializesSessionOnceAndReadsStructuredSseResults() throws Exception {
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    var initialized=new AtomicInteger();var notifications=new AtomicInteger();var calls=new AtomicInteger();
    var failures=new ArrayList<String>();
    server.createContext("/mcp",exchange->{
      try {
        var request=mapper.readTree(exchange.getRequestBody());String method=request.path("method").asText();
        if(method.equals("notifications/initialized")){notifications.incrementAndGet();exchange.sendResponseHeaders(202,-1);return;}
        var result=mapper.createObjectNode();
        if(method.equals("initialize")){initialized.incrementAndGet();exchange.getResponseHeaders().set("Mcp-Session-Id","test-session");result.put("protocolVersion","2025-03-26");}
        else {
          calls.incrementAndGet();
          if(!"test-session".equals(exchange.getRequestHeaders().getFirst("Mcp-Session-Id")))failures.add("missing session");
          if(!"2025-03-26".equals(exchange.getRequestHeaders().getFirst("MCP-Protocol-Version")))failures.add("missing protocol");
          result.putObject("structuredContent").putArray("moods").addObject().put("name","joyful");
        }
        var response=mapper.createObjectNode().put("jsonrpc","2.0");response.set("id",request.get("id"));response.set("result",result);
        String payload=method.equals("initialize")?response.toString():"event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\nevent: message\ndata: "+response+"\n\n";
        exchange.getResponseHeaders().set("Content-Type",method.equals("initialize")?"application/json":"text/event-stream");
        byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
      }finally{exchange.close();}
    });server.start();
    try {
      var client=new PoemShipMcpClient(mapper,"http://127.0.0.1:"+server.getAddress().getPort()+"/mcp");
      assertEquals("joyful",client.call("library_guide",Map.of("language","en")).path("moods").get(0).path("name").asText());
      client.call("library_guide",Map.of("language","ru"));
      assertEquals(1,initialized.get());assertEquals(1,notifications.get());assertEquals(2,calls.get());assertTrue(failures.isEmpty(),failures.toString());
      assertThrows(IOException.class,()->client.call("arbitrary_tool",Map.of()));
    }finally{server.stop(0);}
  }
  @Test void unavailableEndpointReturnsCheckedFailure() {
    var client=new PoemShipMcpClient(mapper,"http://127.0.0.1:1/mcp");
    assertThrows(IOException.class,()->client.call("library_guide",Map.of("language","en")));
  }
}
