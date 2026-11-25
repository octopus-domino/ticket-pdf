package ru.ticket.journey.poetry;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/** Reads the publisher's structured full text after MCP has selected a work. */
@Component
public class PoemShipTextResolver {
  private final ObjectMapper mapper;
  public PoemShipTextResolver(ObjectMapper mapper){this.mapper=mapper;}
  public Optional<PoetryWork> resolve(JsonNode candidate,PoetryQuery query) throws IOException {
    String id=candidate.path("id").asText();URI supplied;
    try{supplied=URI.create(candidate.path("url").asText());}catch(IllegalArgumentException invalid){return Optional.empty();}
    if(!id.matches("[a-fA-F0-9]{24}")||!"https".equals(supplied.getScheme())||supplied.getUserInfo()!=null||supplied.getPort()!=-1||supplied.getHost()==null
        ||!Set.of("poemship.com","poets.by").contains(supplied.getHost())||!("/poems/"+id).equals(supplied.getPath()))return Optional.empty();
    // Drop tracking/query parameters. Never fetch an arbitrary URL returned by an external tool.
    String url="https://"+supplied.getHost()+"/poems/"+id;
    var connection=(HttpURLConnection)URI.create(url).toURL().openConnection();
    try{
      connection.setConnectTimeout(PoetryFetchBudget.timeout(3000));connection.setReadTimeout(PoetryFetchBudget.timeout(5000));connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("User-Agent","CoDoc/1.0 (https://codoc.site)");connection.setRequestProperty("Accept","text/html");
      if(connection.getResponseCode()!=200)throw new IOException("Poetry publisher unavailable");
      byte[] bytes;try(var input=connection.getInputStream()){bytes=input.readNBytes(750001);}
      if(bytes.length>750000)throw new IOException("Poetry publisher page too large");
      return parse(new String(bytes,StandardCharsets.UTF_8),candidate,query,url);
    }finally{connection.disconnect();}
  }
  Optional<PoetryWork> parse(String html,JsonNode candidate,PoetryQuery query,String url) throws IOException {
    var document=Jsoup.parse(html);
    String editorial=document.select("[class*='SummaryCard-'][class*='__excerpt']").text();
    if(!query.audience().equals("child")&&PoetryAudienceEvidence.childrenGenre(editorial,query.language()))
      throw new PoetryAudienceEvidence.Mismatch();
    var poem=document.selectFirst("article .poem-text");
    if(poem==null)return Optional.empty();
    var verses=poem.select("span[class*='__line']").eachText();
    if(verses.isEmpty())return Optional.empty();
    var stanzas=new ArrayList<String>();
    for(var stanza:poem.select("p[class*='__stanza']")){
      var lines=stanza.select("span[class*='__line']").eachText();if(!lines.isEmpty())stanzas.add(String.join("\n",lines));
    }
    String body=(stanzas.isEmpty()?String.join("\n",verses):String.join("\n\n",stanzas)).strip();
    for(var script:document.select("script[type=application/ld+json]")){
      JsonNode work;try{work=mapper.readTree(script.data());}catch(IOException invalid){continue;}
      if(!Set.of("CreativeWork","Poem").contains(work.path("@type").asText())||!work.has("text"))continue;
      String title=work.path("name").asText(),author=work.path("author").path("name").asText();
      String excerpt=PoetryDbProvider.normalize(work.path("text").asText());
      int expected=candidate.path("lines").asInt(0);long lines=body.lines().filter(line->!line.isBlank()).count();
      if(!PoetryDbProvider.normalize(title).equals(PoetryDbProvider.normalize(candidate.path("title").asText()))
          ||!PoetryDbProvider.normalize(author).equals(PoetryDbProvider.normalize(candidate.path("author").asText()))
          ||!work.path("inLanguage").asText().equals(query.language())||body.isBlank()||body.length()>query.maxCharacters()
          ||expected<=0||lines!=expected||lines>query.maxLines()||excerpt.isBlank()||!PoetryDbProvider.normalize(body).startsWith(excerpt))continue;
      String license=work.path("license").asText("");if(!license.startsWith("https://"))license="";
      return Optional.of(new PoetryWork(title,body,author,"","","",url,license,Map.of("source","poemship","text_complete",true,"audience_review","publisher_genre")));
    }
    return Optional.empty();
  }
}
