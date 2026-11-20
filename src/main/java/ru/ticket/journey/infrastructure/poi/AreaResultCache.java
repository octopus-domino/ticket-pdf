package ru.ticket.journey.infrastructure.poi;

import java.time.Duration;
import java.util.*;
import ru.ticket.journey.PlaceRecommendationsController.Place;

/** Reuses only fully covered circles with identical filters; empty successful results are valid too. */
final class AreaResultCache {
  private static final int CAPACITY = 64;
  private static final long TTL = Duration.ofMinutes(10).toNanos();
  private record Entry(WalkAreaSearchService.Request request, List<Place> places, long expires) {}
  private final Deque<Entry> entries = new ArrayDeque<>();

  synchronized List<Place> get(WalkAreaSearchService.Request request) {
    entries.removeIf(entry -> entry.expires() <= System.nanoTime());
    return entries.stream().filter(entry -> covers(entry.request(), request)).findFirst()
        .map(entry -> entry.places().stream().filter(place -> distance(request, place) <= request.radiusKm() * 1000)
            .sorted(Comparator.comparingDouble(place -> distance(request, place))).toList()).orElse(null);
  }

  synchronized void put(WalkAreaSearchService.Request request, List<Place> places) {
    entries.removeIf(entry -> entry.request().equals(request) || entry.expires() <= System.nanoTime());
    entries.addFirst(new Entry(request, List.copyOf(places), System.nanoTime() + TTL));
    while (entries.size() > CAPACITY) entries.removeLast();
  }

  private boolean covers(WalkAreaSearchService.Request saved, WalkAreaSearchService.Request requested) {
    return saved.themes().equals(requested.themes()) && saved.language().equals(requested.language())
        && Objects.equals(saved.visitTime(), requested.visitTime())
        && WikimapiaPoiSourceImpl.distance(saved.longitude(), saved.latitude(), requested.longitude(), requested.latitude())
            + requested.radiusKm() * 1000 <= saved.radiusKm() * 1000;
  }

  private double distance(WalkAreaSearchService.Request request, Place place) {
    return WikimapiaPoiSourceImpl.distance(request.longitude(), request.latitude(), place.longitude(), place.latitude());
  }
}
