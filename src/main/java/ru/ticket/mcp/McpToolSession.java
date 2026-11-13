package ru.ticket.mcp;

import com.fasterxml.jackson.databind.*;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/** Allowlisted tools, bounded output and a short circuit breaker; payloads never reach logs. */
final class McpToolSession {
  private final Supplier<McpSyncClient> client;
  private final ObjectMapper mapper;
  private final Set<String> allowed;
  private final Semaphore permit=new Semaphore(1);
  private boolean initialized;
  private volatile long retryAt;
  McpToolSession(Supplier<McpSyncClient> client,ObjectMapper mapper,Set<String> allowed){this.client=client;this.mapper=mapper;this.allowed=allowed;}
  boolean enabled(){return client.get()!=null;}
  JsonNode call(String name,Map<String,Object> arguments) throws IOException {
    if(!allowed.contains(name)||System.currentTimeMillis()<retryAt||!permit.tryAcquire())throw new IOException("MCP temporarily unavailable");
    try{
      var connection=client.get();if(connection==null)throw new IOException("MCP disabled");
      if(!initialized){if(!connection.isInitialized())connection.initialize();initialized=true;}
      var result=connection.callTool(new McpSchema.CallToolRequest(name,arguments));
      if(Boolean.TRUE.equals(result.isError()))throw new IOException("MCP tool unavailable");
      if(result.structuredContent()!=null)return mapper.valueToTree(result.structuredContent());
      for(var content:result.content())if(content instanceof McpSchema.TextContent text&&text.text().length()<=1_000_000){
        try{var value=mapper.readTree(text.text());if(value!=null)return value;}catch(IOException invalid){/* No prose inference. */}
      }
      throw new IOException("MCP returned no structured data");
    }catch(IOException unavailable){failed();throw unavailable;}
    catch(RuntimeException unavailable){initialized=false;failed();throw new IOException("MCP temporarily unavailable");}
    finally{permit.release();}
  }
  private void failed(){retryAt=System.currentTimeMillis()+60000;org.slf4j.LoggerFactory.getLogger(getClass()).warn("mcp.source.unavailable tools={}",allowed);}
}
