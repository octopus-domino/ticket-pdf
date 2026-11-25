package ru.ticket.journey;
import java.util.Set;
/** Shipped artwork IDs only; a site cannot inject a remote background URL. */
final class JourneyBackgrounds {
  private static final Set<String> IDS = Set.of("sage-paper", "rose-petals", "lavender-mist", "citrus-garden", "wildflower-meadow", "fern-study", "cherry-blossom", "olive-grove", "tropical-sand", "autumn-herbarium", "pearl-flow", "apricot-haze", "blue-current", "lilac-glass", "seafoam-orbit", "terracotta-wave", "champagne-light", "silver-linework", "mint-lilac", "dawn-gradient", "cotton-ivory", "linen-cream", "white-marble", "travertine-sand", "handmade-washi", "watercolor-ivory", "chalk-plaster", "silk-pearl", "pale-wood", "recycled-paper", "gold-celebration", "peach-ribbon", "blue-confetti", "winter-snow", "spring-watercolor", "art-deco-ivory", "champagne-bokeh", "dusty-blue-floral", "terracotta-arch", "coral-festival", "misty-mountains", "coastal-morning", "forest-haze", "desert-dunes", "alpine-lake", "city-sketch", "island-breeze", "northern-light", "tuscan-hills", "sakura-journey", "corporate-blue", "graphite-grid", "teal-geometry", "executive-ivory", "navy-gold", "steel-wave", "light-blueprint", "sand-architecture", "emerald-lines", "platinum-paper");
  static boolean valid(String id) { return id == null || IDS.contains(id); }
}
