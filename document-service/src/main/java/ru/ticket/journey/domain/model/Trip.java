package ru.ticket.journey.domain.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/** Versioned itinerary with explicit days, cities, travel and lodging. */
public record Trip(int version, String id, String title, String description,
    LocalDate startDate, LocalDate endDate, String baseCurrency, List<Traveler> travelers,
    List<City> cities, List<Day> days, List<Leg> legs, List<Accommodation> accommodations,
    List<Document> documents, Budget budget, Meta meta) {

  /** Copies collections and enforces the minimum calendar range for travel mode. */
  public Trip {
    if (version != 2 || meta == null) throw new IllegalArgumentException("journey.trip.invalid-version");
    if (meta.mode() == Mode.TRIP) new TripDates(startDate, endDate);
    travelers = List.copyOf(travelers);
    cities = List.copyOf(cities);
    days = List.copyOf(days);
    legs = List.copyOf(legs);
    accommodations = List.copyOf(accommodations);
    documents = List.copyOf(documents);
  }

  /** Distinguishes historical walks from multi-day travel. */
  public enum Mode { WALK, TRIP }
  /** Identifies how the itinerary was created. */
  public enum Source { MANUAL, PRIVATE_JOURNEY }
  /** Transport modes supported by the itinerary. */
  public enum Transport { PLANE, TRAIN, BUS, CAR, FERRY, WALK }
  /** Categories for activities within a day. */
  public enum Category { FOOD, MUSEUM, WALK, SHOPPING, TRANSPORT, OTHER }
  /** Origin of accommodation details. */
  public enum HotelProvider { OSTROVOK_MCP, MANUAL, RUSSIAN_TOURISM }
  /** A participant whose contact details stay in the private itinerary. */
  public record Traveler(String name, String email) {}
  /** Named destination and its display time zone. Coordinates may be unavailable on import. */
  public record City(String id, String name, String country,
      Double latitude, Double longitude, String timezone, String geonameId) {}
  /** A calendar day with ordered activities and an optional accommodation. */
  public record Day(LocalDate date, String cityId, List<Segment> segments, String accommodationId, String note,
      List<WalkingSection> walkingSections) {
    /** Copies the activity order. */
    public Day {
      segments = List.copyOf(segments);
      walkingSections = walkingSections == null ? List.of() : List.copyOf(walkingSections);
    }

    /** Creates a day without stored walking geometry. */
    public Day(LocalDate date, String cityId, List<Segment> segments, String accommodationId, String note) {
      this(date, cityId, segments, accommodationId, note, List.of());
    }
  }
  /** Coordinate in named latitude/longitude order, independent of map serialization. */
  public record Coordinate(double latitude, double longitude) {}
  /** Geometry between consecutive walking activities. */
  public record WalkingSection(String fromSegmentId, String toSegmentId, List<Coordinate> geometry,
      double distanceMeters, double durationSeconds) {
    /** Takes an immutable copy of the transferred geometry. */
    public WalkingSection {
      geometry = List.copyOf(geometry);
    }
  }
  /** Activity or idea at a location, with local times and optional cost. */
  public record Segment(String id, String title, Double latitude, Double longitude, String address,
      LocalTime startTime, LocalTime endTime, Category category, Money cost, String note,
      List<String> photoRefs, boolean idea, @JsonInclude(JsonInclude.Include.NON_NULL) String walkId,
      @JsonInclude(JsonInclude.Include.NON_NULL) String sourceUrl,
      @JsonInclude(JsonInclude.Include.NON_NULL) String walkNote,
      @JsonInclude(JsonInclude.Include.NON_EMPTY) List<RoutePhoto> photoCredits) {
    /** Copies the accompanying photo references. */
    public Segment {
      photoRefs = photoRefs == null ? List.of() : List.copyOf(photoRefs);
      photoCredits = photoCredits == null ? List.of() : List.copyOf(photoCredits);
    }

    public Segment(String id, String title, Double latitude, Double longitude, String address, LocalTime startTime,
        LocalTime endTime, Category category, Money cost, String note, List<String> photoRefs, boolean idea) {
      this(id, title, latitude, longitude, address, startTime, endTime, category, cost, note, photoRefs, idea, null, null, null, List.of());
    }
  }
  /** Travel between two destinations with times in their own zones. */
  public record Leg(String id, String fromCityId, String toCityId, Transport transport,
      @JsonFormat(shape = JsonFormat.Shape.STRING, without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
      ZonedDateTime departureAt,
      @JsonFormat(shape = JsonFormat.Shape.STRING, without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
      ZonedDateTime arrivalAt, Money cost, String bookingRef,
      String bookingUrl, String note, List<Coordinate> geometry, Double distanceMeters, Double durationSeconds) {
    /** Copies road geometry and accepts historical transfers with no saved route. */
    public Leg {
      geometry = geometry == null ? List.of() : List.copyOf(geometry);
    }

    /** Creates a transfer without calculated road geometry. */
    public Leg(String id, String fromCityId, String toCityId, Transport transport, ZonedDateTime departureAt,
        ZonedDateTime arrivalAt, Money cost, String bookingRef, String bookingUrl, String note) {
      this(id, fromCityId, toCityId, transport, departureAt, arrivalAt, cost, bookingRef, bookingUrl, note,
          List.of(), null, null);
    }
  }
  /** Selected lodging and its calendar dates. */
  public record Accommodation(String id, String cityId, String name, String address,
      Double latitude, Double longitude, LocalDate checkIn, LocalDate checkOut, Money cost,
      String bookingUrl, HotelProvider provider, String note) {}
  /** Reference to a supporting document. */
  public record Document(String id, String title, String url) {}
  /** Amount in its original currency. */
  public record Money(BigDecimal amount, String currency) {}
  /** Optional amount set aside for the trip. */
  public record Budget(Money limit) {}
  /** Provenance and legacy fields that have no dedicated v2 field. */
  public record Meta(Mode mode, Source source, String organizer, String contact,
      String legacyRouteId, Map<String, String> legacyFields) {
    /** Preserves a snapshot of the legacy fields. */
    public Meta {
      legacyFields = legacyFields == null ? Map.of() : Map.copyOf(legacyFields);
    }
  }
}
