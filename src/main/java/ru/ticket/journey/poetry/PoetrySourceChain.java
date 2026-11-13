package ru.ticket.journey.poetry;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;
import org.springframework.http.HttpStatus;

/** Ordered failover and bounded curation cache, separate from PDF rendering capacity. */
@Service
public class PoetrySourceChain {
  private record Cached(long until,List<PoetryWork> works) {}
  private final List<PoetryProvider> providers;
  private final WikisourcePoems wiki;
  private final Semaphore permit=new Semaphore(1);
  private final Map<String,Cached> cache=new LinkedHashMap<>(32,.75f,true);
  public PoetrySourceChain(PoemShipProvider ship,PoetryDbProvider db,PoeTreeProvider tree,WikisourcePoems wiki){this.providers=List.of(ship,db,tree);this.wiki=wiki;}
  public List<PoetryWork> select(PoetryQuery query) {
    String key=query.language()+":"+query.audience()+":"+query.mood()+":"+Objects.toString(query.moodText(),"")+":"+query.need();
    synchronized(cache){var found=cache.get(key);if(found!=null&&found.until>System.currentTimeMillis()){
      if(found.works.size()<7)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"journey.poetry.not-enough");
      return seven(found.works,query.seed());}}
    if(!permit.tryAcquire())throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"journey.poetry.busy");
    try(var budget=PoetryFetchBudget.open(Duration.ofSeconds(90))){
      var works=new ArrayList<PoetryWork>();var seen=new HashSet<String>();
      for(var provider:providers){
        try{
          for(var work:provider.find(query))if(seen.add(PoetryDbProvider.normalize(work.author()+" "+work.title())))works.add(work);
        }catch(IOException|ApiException unavailable){/* Provider failures do not become fake successful poems. */}
        if(works.size()>=7)break;
      }
      if(works.size()<7){
        try{
          var selected=PoetrySelection.select(wiki.poems(query.language(),query.mood(),query.audience()),query.days(),query.language(),query.mood(),query.audience(),query.seed());
          for(var poem:selected){var work=PoetryWork.wiki(poem,query.language(),query.mood());String identity=PoetryDbProvider.normalize(work.author()+" "+work.title());
            if(works.stream().noneMatch(existing->PoetryDbProvider.normalize(existing.author()+" "+existing.title()).equals(identity)))works.add(work);}
        }catch(ApiException unavailable){/* Preserve any curated hits, but never return fewer than seven. */}
      }
      if(works.size()<7){
        synchronized(cache){cache.put(key,new Cached(System.currentTimeMillis()+Duration.ofMinutes(2).toMillis(),List.of()));trimCache();}
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"journey.poetry.not-enough");
      }
      var result=List.copyOf(works);
      synchronized(cache){cache.put(key,new Cached(System.currentTimeMillis()+Duration.ofHours(6).toMillis(),result));trimCache();}
      return seven(result,query.seed());
    }finally{permit.release();}
  }
  private void trimCache(){while(cache.size()>32)cache.remove(cache.keySet().iterator().next());}
  private static List<PoetryWork> seven(List<PoetryWork> works,String seed){
    // Preserve provider ranking; optional seed rotates only within equally curated results.
    if(works.size()==7)return works;
    int start=Math.floorMod(Objects.hashCode(seed),works.size());var result=new ArrayList<PoetryWork>();
    for(int i=0;i<7;i++)result.add(works.get((start+i)%works.size()));return List.copyOf(result);
  }
}
