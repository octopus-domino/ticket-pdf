package ru.ticket.journey.application.journey;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;
import ru.ticket.journey.JourneyBranding;
import ru.ticket.journey.JourneyFieldLabels;
import ru.ticket.journey.JourneyService.Input;
import ru.ticket.journey.JourneyService.Kind;
import ru.ticket.journey.JourneyService.View;
import ru.ticket.journey.ProposalContent;
import ru.ticket.journey.domain.model.JourneyEntity;
import ru.ticket.journey.poetry.PoetryCardsService;

/** Shared validation and persistence conversion for publication workflows. */
@Component
public class JourneyPublicationData {
  private final ObjectMapper mapper;
  private final PoetryCardsService poetry;

  /** Wires the publication codec and the existing poetry bundle lookup. */
  public JourneyPublicationData(ObjectMapper mapper, PoetryCardsService poetry) {
    this.mapper = mapper;
    this.poetry = poetry;
  }

  /** Checks that a referenced poetry collection exists. */
  public void validatePoetry(Kind kind, Map<String, String> data) {
    if (kind == Kind.POETRY) poetry.get(data.get("poetry"));
  }

  /** Decodes a stored publication for the existing public view. */
  public View view(JourneyEntity entity) {
    try {
      return new View(
          entity.id,
          Kind.valueOf(entity.kind),
          entity.title,
          mapper.readValue(entity.dataJson, new TypeReference<Map<String, String>>() {}),
          entity.updatedAt);
    } catch (java.io.IOException error) {
      throw new IllegalStateException("Stored journey is invalid", error);
    }
  }

  /** Serializes the publication fields for storage. */
  public String json(Map<String, String> data) {
    try {
      return mapper.writeValueAsString(data);
    } catch (java.io.IOException error) {
      throw new IllegalArgumentException("Invalid document data", error);
    }
  }

  /** Validates input fields before creating, updating or previewing a publication. */
  public static void validate(Input input) {
    if (input == null
        || input.kind() == null
        || input.title() == null
        || input.title().isBlank()
        || input.title().length() > 120
        || input.data() == null
        || input.data().size() > 20)
      throw ApiException.badRequest("Please complete the document title");
    JourneyBranding.parse(input.data().get("branding"));
    JourneyFieldLabels.read(input.data().get("field_labels"));
    JourneyFieldLabels.read(input.data().get("route_labels"));
    String route = input.data().get("route");
    if (route != null && !route.isBlank() && !route.matches("[a-f0-9]{64}"))
      throw ApiException.badRequest("journey.route.invalid-route");
    for (var field : input.data().entrySet()) {
      int limit =
          (input.kind() == Kind.PROPOSAL && field.getKey().equals("positions"))
                  || (input.kind() == Kind.SCHEDULE && field.getKey().equals("events"))
              ? 80000
              : 12000;
      if (!field.getKey().matches("[a-z][a-z0-9_]{0,31}")
          || Set.of("__proto__", "constructor", "prototype").contains(field.getKey())
          || field.getValue() == null
          || field.getValue().length() > limit)
        throw ApiException.badRequest("Invalid or oversized document field");
    }
    if (input.data().values().stream().mapToInt(String::length).sum()
        > (Set.of(Kind.PROPOSAL, Kind.SCHEDULE).contains(input.kind()) ? 100000 : 32000))
      throw ApiException.badRequest("Document fields are too large");
    if (input.kind() == Kind.PROPOSAL) {
      ProposalContent.read(input.data().getOrDefault("positions", "[]"));
      for (String key : List.of("issuer", "recipient"))
        if (input.data().getOrDefault(key, "").length() > 100)
          throw ApiException.badRequest("Company and recipient: maximum 100 characters");
      if (!input.data().getOrDefault("currency", "RUB").matches("[A-Z]{3}"))
        throw ApiException.badRequest("Use a three-letter currency code, e.g. RUB");
    }
    if (input.data().getOrDefault("items", "").lines().count() > 150)
      throw ApiException.badRequest("A menu can contain up to 150 lines");
  }

  /** Computes the existing SHA-256 digest for an opaque publication key. */
  public static String hash(String key) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
