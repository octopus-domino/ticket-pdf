package ru.ticket.journey.domain.service;

import java.util.List;

/** Resolves settlement names and coordinates. */
public interface CitySearch {
  /** One settlement returned by the configured gazetteer. */
  record City(String name, String label, double latitude, double longitude) {}

  /** Looks up named settlements in the requested language. */
  List<City> search(String query, String language);
}
