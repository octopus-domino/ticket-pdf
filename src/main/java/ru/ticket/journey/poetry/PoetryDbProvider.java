package ru.ticket.journey.poetry;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PoetryDbProvider implements PoetryProvider {
  private final PoetryRemoteJson remote;
  public PoetryDbProvider(PoetryRemoteJson remote){this.remote=remote;}
  public String name(){return "poetrydb";}
  public List<PoetryWork> find(PoetryQuery query) throws IOException {
    // The dataset has no reliable age labels. Never substitute unclassified texts for children.
    if(!query.language().equals("en")||Set.of("child","teen").contains(query.audience()))return List.of();
    return read(remote.get("https://poetrydb.org/random/20",500000,6000),query).stream().filter(work->work.suitableFallback(query)).toList();
  }
  public Optional<PoetryWork> resolve(String title,String author,PoetryQuery query) throws IOException {
    if(!query.language().equals("en"))return Optional.empty();
    String url="https://poetrydb.org/author,title/"+PoetDiscovery.encode(author)+";"+PoetDiscovery.encode(title);
    return read(remote.get(url,500000,6000),query).stream().filter(work->normalize(work.title()).equals(normalize(title))&&normalize(work.author()).equals(normalize(author))).findFirst();
  }
  private List<PoetryWork> read(JsonNode payload,PoetryQuery query){
    if(!payload.isArray())return List.of();var result=new ArrayList<PoetryWork>();
    for(var item:payload){
      if(result.size()>=20)break;
      String title=item.path("title").asText(""),author=item.path("author").asText("");
      var lines=new ArrayList<String>();item.path("lines").forEach(line->lines.add(line.asText()));String body=String.join("\n",lines).strip();
      if(title.isBlank()||author.isBlank()||body.isBlank()||body.length()>query.maxCharacters()||lines.size()>query.maxLines())continue;
      String url="https://poetrydb.org/title/"+PoetDiscovery.encode(title);
      result.add(new PoetryWork(title,body,author,"", "", "",url,"https://creativecommons.org/licenses/by/4.0/",
          Map.of("source",name(),"language","en","mood",query.mood(),"text_complete",true,"curation","unclassified")));
    }return List.copyOf(result);
  }
  static String normalize(String value){return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
}
