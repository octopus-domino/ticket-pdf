package ru.ticket.routing.trip;

import com.fasterxml.jackson.databind.JsonNode;

public interface TripAccountGateway {
  String owner(String authorization);
  JsonNode legacyTrip(String authorization, String id);
}
