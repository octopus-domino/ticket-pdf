package ru.ticket.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.*;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** HTTP-only clients are constructed locally; network initialization is lazy in the adapters. */
@Configuration
public class McpClientFactory {
  @Bean(name="osmMcpSyncClient",destroyMethod="close")
  @ConditionalOnProperty(name="app.mcp.osm.enabled",havingValue="true",matchIfMissing=true)
  McpSyncClient osm(ObjectMapper mapper,@Value("${app.mcp.osm.url:http://localhost:8000/mcp}") String url){return create(mapper,url);}

  @Bean(name="commonsMcpSyncClient",destroyMethod="close")
  @ConditionalOnProperty(name="app.mcp.commons.enabled",havingValue="true",matchIfMissing=true)
  McpSyncClient commons(ObjectMapper mapper,@Value("${app.mcp.commons.url:https://gateway.pipeworx.io/commons-wikimedia/mcp}") String url){return create(mapper,url);}

  public static McpSyncClient create(ObjectMapper mapper,String url){
    var uri=URI.create(url);
    if(!java.util.Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)
      throw new IllegalArgumentException("Invalid MCP endpoint configuration");
    String origin=uri.getScheme()+"://"+uri.getRawAuthority();
    String path=uri.getRawPath().isBlank()?"/mcp":uri.getRawPath();
    var transport=HttpClientStreamableHttpTransport.builder(origin).endpoint(path)
        .jsonMapper(new JacksonMcpJsonMapper(mapper.copy()))
        .clientBuilder(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER))
        .connectTimeout(Duration.ofSeconds(3)).maxResponseSize(1_000_000)
        .requestBuilder(HttpRequest.newBuilder().timeout(Duration.ofSeconds(15)))
        .build();
    return McpClient.sync(transport).initializationTimeout(Duration.ofSeconds(3))
        .requestTimeout(Duration.ofSeconds(15)).enableCallToolSchemaCaching(false).build();
  }
}
