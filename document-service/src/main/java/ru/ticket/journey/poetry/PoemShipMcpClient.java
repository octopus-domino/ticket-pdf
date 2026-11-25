package ru.ticket.journey.poetry;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Minimal bounded Streamable HTTP client. Tool output is data, never executable instructions. */
@Component
public class PoemShipMcpClient {
  private final ObjectMapper mapper;
  private final String endpoint;
  private String session,protocol="2025-03-26";
  private long sequence;
  private boolean initialized;
  private final java.util.concurrent.Semaphore permit=new java.util.concurrent.Semaphore(1);
  public PoemShipMcpClient(ObjectMapper mapper,@Value("${app.poetry.poemship-url:https://poemship.com/mcp}") String endpoint){this.mapper=mapper;this.endpoint=endpoint;}
  public JsonNode call(String tool,Map<String,Object> arguments) throws IOException {
    if(!Set.of("library_guide","find_poems","get_poem").contains(tool))throw new IOException("Unsupported poetry tool");
    if(!permit.tryAcquire())throw new IOException("Poetry MCP is busy");
    try {
      if(!initialized){
        var result=request("initialize",Map.of("protocolVersion",protocol,"capabilities",Map.of(),"clientInfo",Map.of("name","codoc","version","1.0")),false);
        protocol=result.path("protocolVersion").asText(protocol);
        if(!Set.of("2024-11-05","2025-03-26","2025-06-18","2025-11-25").contains(protocol))throw new IOException("Unsupported MCP protocol");
        initialized=true;request("notifications/initialized",Map.of(),true);
      }
      var result=request("tools/call",Map.of("name",tool,"arguments",arguments),false);
      if(result.path("isError").asBoolean())throw new IOException("Poetry tool rejected request");
      if(result.has("structuredContent"))return result.path("structuredContent");
      for(var content:result.path("content"))if(content.path("type").asText().equals("text")){
        try{return mapper.readTree(content.path("text").asText());}catch(IOException invalid){/* No markdown inference or invented schema. */}
      }
      throw new IOException("Poetry tool returned no structured data");
    }catch(IOException error){initialized=false;session=null;throw error;}finally{permit.release();}
  }
  private JsonNode request(String method,Map<String,Object> parameters,boolean notification) throws IOException {
    long id=++sequence;var message=new LinkedHashMap<String,Object>();message.put("jsonrpc","2.0");
    if(!notification)message.put("id",id);message.put("method",method);message.put("params",parameters);
    var connection=(HttpURLConnection)URI.create(endpoint).toURL().openConnection();
    try {
      connection.setConnectTimeout(PoetryFetchBudget.timeout(3000));connection.setReadTimeout(PoetryFetchBudget.timeout(8000));connection.setInstanceFollowRedirects(false);
      connection.setRequestMethod("POST");connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("Accept","application/json, text/event-stream");
      connection.setRequestProperty("User-Agent","CoDoc/1.0 (https://codoc.site)");
      if(session!=null)connection.setRequestProperty("Mcp-Session-Id",session);
      if(initialized)connection.setRequestProperty("MCP-Protocol-Version",protocol);
      byte[] payload=mapper.writeValueAsBytes(message);connection.setFixedLengthStreamingMode(payload.length);
      try(var output=connection.getOutputStream()){output.write(payload);}
      int status=connection.getResponseCode();
      if(status<200||status>=300)throw new IOException("Poetry MCP HTTP "+status);
      String next=connection.getHeaderField("Mcp-Session-Id");if(next!=null&&next.length()<=512&&!next.contains("\n")&&!next.contains("\r"))session=next;
      if(notification)return mapper.createObjectNode();
      if(Objects.toString(connection.getContentType(),"").contains("text/event-stream")){
        try(var reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8))){
          var data=new StringBuilder();int total=0;String line;
          while((line=reader.readLine())!=null){
            PoetryFetchBudget.timeout(8000);
            total+=line.length();if(total>1_000_000)throw new IOException("Poetry MCP response too large");
            if(line.isEmpty()&&!data.isEmpty()){
              var response=mapper.readTree(data.toString());data.setLength(0);
              if(response.path("id").asLong(-1)==id)return unwrap(response,id);
            }else if(line.startsWith("data:")){if(!data.isEmpty())data.append('\n');data.append(line.substring(5).stripLeading());}
          }
        }
        throw new IOException("No matching poetry MCP event");
      }
      byte[] bytes;try(var input=connection.getInputStream()){bytes=input.readNBytes(1_000_001);}
      if(bytes.length>1_000_000)throw new IOException("Poetry MCP response too large");
      String body=new String(bytes,StandardCharsets.UTF_8);
      if(body.stripLeading().startsWith("{"))return unwrap(mapper.readTree(body),id);
      for(String block:body.replace("\r\n","\n").split("\n\n")){
        String data=block.lines().filter(line->line.startsWith("data:")).map(line->line.substring(5).stripLeading()).reduce((a,b)->a+"\n"+b).orElse("");
        if(!data.isBlank()){
          var response=mapper.readTree(data);
          if(response.path("id").asLong(-1)==id)return unwrap(response,id);
        }
      }
      throw new IOException("No matching poetry MCP response");
    }finally{connection.disconnect();}
  }
  private JsonNode unwrap(JsonNode response,long id) throws IOException {
    if(response==null||!response.path("jsonrpc").asText().equals("2.0")||response.path("id").asLong(-1)!=id||response.has("error")||!response.has("result"))
      throw new IOException("Invalid poetry MCP response");
    return response.path("result");
  }
}
