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
  private static final int CARDS = 7;
  private record Cached(long until, List<PoetryWork> works, boolean expanded) {}
  private final List<PoetryProvider> providers;
  private final WikisourcePoems wiki;
  private final Semaphore permit=new Semaphore(1);
  private final Map<String,Cached> cache=new LinkedHashMap<>(32,.75f,true);
  public PoetrySourceChain(PoemShipProvider ship,PoetryDbProvider db,PoeTreeProvider tree,WikisourcePoems wiki){this.providers=List.of(ship,db,tree);this.wiki=wiki;}
  public List<PoetryWork> select(PoetryQuery query) {
    return select(query, Set.of());
  }

  public List<PoetryWork> select(PoetryQuery query, Set<String> previous) {
    String key = query.language()+":"+query.audience()+":"+query.mood()+":"+Objects.toString(query.moodText(),"")+":"+query.need();
    Cached cached;
    synchronized (cache) {
      cached = cache.get(key);
      if (cached != null && cached.until <= System.currentTimeMillis()) cached = null;
    }
    boolean expand = !previous.isEmpty() && (query.moodText() == null || query.moodText().isBlank());
    if (cached != null && (cached.works.isEmpty() || !expand || cached.expanded
        || cached.works.stream().filter(work -> !previous.contains(work.identity())).count() >= CARDS)) {
      return seven(cached.works, query.seed(), previous);
    }
    if (!permit.tryAcquire()) {
      if (cached != null) return seven(cached.works, query.seed(), previous);
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.poetry.busy");
    }
    try (var budget = PoetryFetchBudget.open(Duration.ofSeconds(90))) {
      var works = new LinkedHashMap<String,PoetryWork>();
      if (cached != null) addWorks(works, cached.works);
      int target = expand ? 2 * CARDS : CARDS;
      for (var provider : providers) {
        try {
          addWorks(works, expand && provider instanceof PoemShipProvider ship ? ship.findExpanded(query) : provider.find(query));
        } catch (IOException | ApiException unavailable) {
          // Fall through to the next source.
        }
        if (works.size() >= target) break;
      }
      if (works.size() < CARDS || expand && works.size() < target) {
        try {
          var candidates = wiki.candidates(query.language(), query.mood(), query.audience());
          if (works.size() < CARDS && candidates.size() >= CARDS) {
            var ranked = PoetrySelection.select(candidates,query.days(),query.language(),query.mood(),query.audience(),query.seed());
            addWorks(works, ranked.stream().map(poem -> PoetryWork.wiki(poem,query.language(),query.mood())).toList());
          }
          addWorks(works, candidates.stream().map(poem -> PoetryWork.wiki(poem,query.language(),query.mood())).toList());
        } catch (ApiException unavailable) {
          // A failed fallback must leave the verified candidates available.
        }
      }
      var result = works.size() < CARDS ? List.<PoetryWork>of() : List.copyOf(works.values());
      var ttl = result.isEmpty() || expand && result.size() < target ? Duration.ofMinutes(2) : Duration.ofHours(6);
      synchronized (cache) {
        cache.put(key, new Cached(System.currentTimeMillis() + ttl.toMillis(), result, expand));
        while (cache.size() > 32) cache.remove(cache.keySet().iterator().next());
      }
      return seven(result, query.seed(), previous);
    } finally {
      permit.release();
    }
  }

  private static void addWorks(Map<String,PoetryWork> works, List<PoetryWork> candidates) {
    for (var work : candidates) works.putIfAbsent(work.identity(), work);
  }

  private static List<PoetryWork> seven(List<PoetryWork> works, String seed, Set<String> previous) {
    if (works.size() < CARDS) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"journey.poetry.not-enough");
    if (works.size() == CARDS) return works;
    int start = Math.floorMod(Objects.hashCode(seed), works.size());
    var ordered = new ArrayList<>(works);
    Collections.rotate(ordered, -start);
    ordered.sort(Comparator.comparing(work -> previous.contains(work.identity())));
    return List.copyOf(ordered.subList(0, CARDS));
  }
}
