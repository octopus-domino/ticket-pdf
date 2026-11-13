package ru.ticket.journey.poetry;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;

/** Provider-side mood/age curation; full-text resolvers never replace an excerpt with a guessed poem. */
@Component
public class PoemShipProvider implements PoetryProvider {
  private record Cached<T>(long until,T value) {}
  private final Map<String,Cached<JsonNode>> guides=new LinkedHashMap<>(32,.75f,true);
  private final Map<String,Cached<List<PoetryWork>>> cache=new LinkedHashMap<>(32,.75f,true);
  private final Semaphore permit=new Semaphore(1);
  private long nextRequest;
  private final PoemShipMcpClient client;
  private final PoetryDbProvider db;
  private final PoeTreeProvider tree;
  private final WikisourcePoems wiki;
  private final PoemShipTextResolver texts;
  public PoemShipProvider(PoemShipMcpClient client,PoetryDbProvider db,PoeTreeProvider tree,WikisourcePoems wiki,PoemShipTextResolver texts){this.client=client;this.db=db;this.tree=tree;this.wiki=wiki;this.texts=texts;}
  public String name(){return "poemship";}
  public JsonNode guide(String language) throws IOException {
    if(!Set.of("en","ru","es","de","fr","it","pl","nl").contains(language))return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    synchronized(guides){var found=guides.get(language);if(found!=null&&found.until>System.currentTimeMillis())return found.value;}
    var value=client.call("library_guide",Map.of("language",language));
    synchronized(guides){guides.put(language,new Cached<>(System.currentTimeMillis()+Duration.ofHours(6).toMillis(),value));while(guides.size()>32)guides.remove(guides.keySet().iterator().next());}
    return value;
  }
  public List<PoetryWork> find(PoetryQuery query) throws IOException {
    String key=query.language()+":"+query.mood()+":"+query.audience()+":"+Objects.toString(query.moodText(),"")+":"+query.need();
    synchronized(cache){var found=cache.get(key);if(found!=null&&found.until>System.currentTimeMillis())return found.value;}
    if(!permit.tryAcquire())return List.of();
    try {
      if(System.currentTimeMillis()<nextRequest)return List.of();nextRequest=System.currentTimeMillis()+1000;
      var guide=guide(query.language());if(!guide.path("moods").isArray()||guide.path("moods").isEmpty())return List.of();
      var args=new LinkedHashMap<String,Object>();args.put("language",query.language());
      boolean ageSupported=Set.of("en","ru","be").contains(query.language());
      // The provider documents advanced age/need filters only for these languages.
      if(!ageSupported&&Set.of("child","teen").contains(query.audience()))return List.of();
      if(ageSupported){args.put("need",query.need());args.put("audience",query.audience().equals("child")?"children":query.audience().equals("teen")?"teens":"anyone");}
      args.put("max_lines",query.maxLines());args.put("limit",10);
      args.put("mood",query.mood().equals("funny")?"joyful":query.need().equals("comfort")?"contemplative":"inspiring");
      if(Set.of("child","teen").contains(query.audience()))args.put("easy_to_read",true);
      if(query.moodText()!=null&&!query.moodText().isBlank()){
        String text=query.moodText().strip();
        boolean known=false;for(var mood:guide.path("moods"))if(mood.path("name").asText().equalsIgnoreCase(text)){args.put("mood",text);known=true;break;}
        if(!known){
          boolean tag=false;for(var item:guide.path("tags"))if(item.asText().equalsIgnoreCase(text)){args.put("tags",List.of(item.asText()));tag=true;break;}
          if(!tag)args.put("query",text);
        }
      }
      var found=client.call("find_poems",args);var result=new ArrayList<PoetryWork>();var seen=new HashSet<String>();
      long deadline=System.nanoTime()+Duration.ofSeconds(55).toNanos();
      for(int pass=0;pass<2;pass++){
        if(pass==1){
          // Broaden only the default mood; never discard an explicitly requested mood/theme or age.
          if(result.size()>=7||query.moodText()!=null&&!query.moodText().isBlank()||System.nanoTime()>deadline)break;
          args.remove("mood");
          try{found=client.call("find_poems",args);}catch(IOException unavailable){break;}
        }
      for(var candidate:found.path("poems")){
        if(result.size()>=10||System.nanoTime()>deadline)break;
        String id=candidate.path("id").asText(""),title=candidate.path("title").asText(""),author=candidate.path("author").asText("");
        String url=candidate.path("url").asText("");
        if(!id.matches("[a-fA-F0-9]{24}")||title.isBlank()||title.length()>300||author.isBlank()||author.length()>300||!seen.add(id)||!trusted(url)
            ||!candidate.path("language").asText(query.language()).equals(query.language()))continue;
        String opening=text(candidate.path("firstLines"));Optional<PoetryWork> full=Optional.empty();
        if(candidate.path("textIsComplete").asBoolean(false)&&!opening.isBlank())
          full=Optional.of(new PoetryWork(title,opening,author,"","","",url,"",Map.of()));
        if(full.isEmpty())try{full=texts.resolve(candidate,query);}catch(IOException unavailable){/* Publisher unavailable: use exact full-text resolvers. */}
        if(full.isEmpty())try{full=db.resolve(title,author,query);}catch(IOException unavailable){/* Next full-text source. */}
        if(full.isEmpty()&&System.nanoTime()<deadline)try{full=tree.resolve(title,author,query);}catch(IOException unavailable){/* Next full-text source. */}
        if(full.isEmpty()&&System.nanoTime()<deadline)try{full=wiki.resolve(title,author,query.language());}catch(IOException unavailable){/* A fragment is not a full poem. */}
        if(full.isEmpty())continue;var work=full.get();
        if(work.text().length()>query.maxCharacters()||work.text().lines().filter(line->!line.isBlank()).count()>query.maxLines())continue;
        String prefix=PoetryDbProvider.normalize(opening),body=PoetryDbProvider.normalize(work.text());
        if(!prefix.isBlank()&&!body.startsWith(prefix))continue;
        var metadata=new LinkedHashMap<String,Object>();metadata.put("source",name());metadata.put("mood",args.getOrDefault("mood",query.mood()));
        metadata.put("tags",args.getOrDefault("tags",List.of()));metadata.put("need",query.need());metadata.put("language",query.language());metadata.put("poemship_id",id);
        metadata.put("text_source",work.metadata().getOrDefault("source",name()));metadata.put("text_complete",true);
        result.add(new PoetryWork(work.title(),work.text(),work.author(),work.authorId(),work.authorUrl(),work.country(),work.sourceUrl(),work.licenseUrl(),Map.copyOf(metadata)));
      }
      }
      var value=List.copyOf(result);
      synchronized(cache){cache.put(key,new Cached<>(System.currentTimeMillis()+Duration.ofMinutes(value.size()>=7?360:2).toMillis(),value));while(cache.size()>32)cache.remove(cache.keySet().iterator().next());}
      return value;
    }finally{permit.release();}
  }
  private static boolean trusted(String value){try{var uri=java.net.URI.create(value);return "https".equals(uri.getScheme())&&Set.of("poemship.com","poets.by").contains(uri.getHost());}catch(RuntimeException invalid){return false;}}
  private static String text(JsonNode value){if(value.isTextual())return value.asText();if(value.isArray()){var lines=new ArrayList<String>();value.forEach(line->{if(line.isTextual())lines.add(line.asText());});return String.join("\n",lines);}return "";}
}
