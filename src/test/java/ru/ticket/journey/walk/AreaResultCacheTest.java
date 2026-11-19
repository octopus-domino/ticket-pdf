package ru.ticket.journey.walk;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AreaResultCacheTest {
  @Test void onlyFullyCoveredCirclesWithSameFiltersReuseEvenAnEmptyResult() {
    var cache = new AreaResultCache();
    cache.put(request(30.3053, 3.1, "bars"), List.of());
    assertEquals(List.of(), cache.get(request(30.3054, .8, "bars")));
    assertNull(cache.get(request(30.3054, 3.1, "bars")));
    assertNull(cache.get(request(30.3053, .8, "cultural")));
  }

  private WalkAreaSearchService.Request request(double longitude, double radius, String theme) {
    return new WalkAreaSearchService.Request(longitude, 59.93155, List.of(theme), "ru", radius, null);
  }
}
