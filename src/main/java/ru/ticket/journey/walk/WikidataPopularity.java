package ru.ticket.journey.walk;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;

/** One batched SPARQL call per uncached set, including 24-hour negative caching. */
@Component
public class WikidataPopularity {
  private final WalkRemote remote;
  private final WalkCache<String,Optional<Integer>> cache=new WalkCache<>(512,Duration.ofHours(24));
  public WikidataPopularity(WalkRemote remote){this.remote=remote;}
  public Map<String,Integer> counts(Collection<String> requested){
    var ids=requested.stream().filter(Objects::nonNull).filter(id->id.matches("Q[1-9][0-9]{0,15}")).distinct().limit(40).toList();
    var missing=ids.stream().filter(id->cache.get(id)==null).toList();
    if(!missing.isEmpty()){
      var found=new HashMap<String,Integer>();
      String values=String.join(" ",missing.stream().map(id->"wd:"+id).toList());
      String query="SELECT ?item ?n WHERE { VALUES ?item { "+values+" } ?item <http://wikiba.se/ontology#sitelinks> ?n }";
      try{
        var payload=remote.get("https://query.wikidata.org/sparql?format=json&query="+WalkRemote.encode(query));
        for(var row:payload.path("results").path("bindings")){
          String id=row.path("item").path("value").asText("").replace("http://www.wikidata.org/entity/","");
          try{int count=Integer.parseInt(row.path("n").path("value").asText());if(missing.contains(id)&&count>=0)found.put(id,count);}catch(NumberFormatException ignored){}
        }
      }catch(IOException unavailable){/* Unknown popularity is never fabricated as zero. */}
      for(var id:missing)cache.put(id,Optional.ofNullable(found.get(id)));
    }
    var result=new HashMap<String,Integer>();for(var id:ids){var value=cache.get(id);if(value!=null)value.ifPresent(count->result.put(id,count));}
    return Map.copyOf(result);
  }
}
