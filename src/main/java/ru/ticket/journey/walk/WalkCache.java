package ru.ticket.journey.walk;

import java.time.Duration;
import java.util.*;

/** Small shared LRU pattern; failed/unknown results can be cached without growing memory. */
public final class WalkCache<K,V> {
  private record Entry<V>(long expires, V value) {}
  private final Map<K,Entry<V>> entries = new LinkedHashMap<>(16,.75f,true);
  private final int capacity;
  private final long ttl;
  public WalkCache(int capacity, Duration ttl) { this.capacity=capacity; this.ttl=ttl.toNanos(); }
  public synchronized V get(K key) {
    var entry=entries.get(key);
    if(entry==null)return null;
    if(System.nanoTime()>=entry.expires){entries.remove(key);return null;}
    return entry.value;
  }
  public synchronized void put(K key,V value) {
    entries.put(key,new Entry<>(System.nanoTime()+ttl,value));
    while(entries.size()>capacity)entries.remove(entries.keySet().iterator().next());
  }
}
