package ru.ticket.walks.domain.service;

import java.io.IOException;
import java.util.List;

/** Searches places of interest in a bounded area. */
public interface PoiSource<P, F> {
  /** Searches near a point using a radius in meters and the requested filter and language. */
  List<P> searchArea(double longitude, double latitude, int radius, F filter, String language)
      throws IOException;
}
