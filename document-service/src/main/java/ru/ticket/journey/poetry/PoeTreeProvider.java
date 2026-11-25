package ru.ticket.journey.poetry;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Documented corpus/author/poem API; never guesses text from an HTTP error or metadata. */
@Component
public class PoeTreeProvider implements PoetryProvider {
  private final PoetryRemoteJson remote;
  private final String endpoint;
  public PoeTreeProvider(PoetryRemoteJson remote,@Value("${app.poetry.poetree-url:https://versologie.cz/poetree/api}") String endpoint){this.remote=remote;this.endpoint=endpoint.replaceAll("/+$","");}
  public String name(){return "poetree";}
  public List<PoetryWork> find(PoetryQuery query) throws IOException {
    if(!Set.of("en","ru","es","de","fr","it").contains(query.language())||Set.of("child","teen").contains(query.audience()))return List.of();
    // Keep the corpus request bounded. Failure or an unsupported response falls through to Wikisource.
    var poems=remote.get(endpoint+"/poems?corpus="+query.language(),500000,5000);
    var result=new ArrayList<PoetryWork>();int checked=0;
    for(var item:poems){if(checked++>=8)break;resolveItem(item,query).ifPresent(result::add);}
    return result.stream().filter(work->work.suitableFallback(query)).toList();
  }
  public Optional<PoetryWork> resolve(String title,String author,PoetryQuery query) throws IOException {
    if(!Set.of("en","ru","es","de","fr","it").contains(query.language()))return Optional.empty();
    var authors=remote.get(endpoint+"/authors?corpus="+query.language(),500000,5000);
    for(var candidate:authors){
      String name=candidate.path("name").asText(candidate.path("name_full").asText(""));
      if(!PoetryDbProvider.normalize(name).equals(PoetryDbProvider.normalize(author)))continue;
      String id=candidate.path("id_").asText("");if(!id.matches("[0-9]{1,12}"))continue;
      var poems=remote.get(endpoint+"/poems?corpus="+query.language()+"&id_author="+id,500000,5000);
      for(var item:poems)if(PoetryDbProvider.normalize(item.path("title").asText()).equals(PoetryDbProvider.normalize(title)))return resolveItem(item,query);
    }return Optional.empty();
  }
  private Optional<PoetryWork> resolveItem(JsonNode item,PoetryQuery query) throws IOException {
    String id=item.path("id_").asText("");if(!id.matches("[0-9]{1,12}"))return Optional.empty();
    String url=endpoint+"/poem?corpus="+query.language()+"&id_poem="+id;
    var poem=remote.get(url,200000,5000);var lines=new ArrayList<String>();
    for(var stanza:poem.path("body")){
      if(stanza.isTextual())lines.add(stanza.asText());
      else for(var line:stanza.path("lines")){String text=line.isTextual()?line.asText():line.path("text").asText("");if(!text.isBlank())lines.add(text);}
      lines.add("");
    }
    String text=String.join("\n",lines).strip(),title=poem.path("title").asText(item.path("title").asText(""));
    String author=poem.path("author").isTextual()?poem.path("author").asText():poem.path("author").path("name").asText("");
    String license=poem.path("license_url").asText("");
    // Corpus accessibility is not a reuse licence for every constituent poem.
    if(text.isBlank()||title.isBlank()||author.isBlank()||text.length()>query.maxCharacters()||text.lines().filter(line->!line.isBlank()).count()>query.maxLines()
        ||!license.startsWith("https://creativecommons.org/"))return Optional.empty();
    return Optional.of(new PoetryWork(title,text,author,id,"","",url,license,Map.of("source",name(),"language",query.language(),"mood",query.mood(),"text_complete",true)));
  }
}
