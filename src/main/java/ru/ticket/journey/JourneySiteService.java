package ru.ticket.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;

/** A site is data: fields, theme and a supported workflow. No generated application code. */
@Service
public class JourneySiteService {
  public record Field(String name, String label, String type, boolean required) {}

  public record Definition(
      String slug,
      JourneyService.Kind kind,
      String title,
      String description,
      String theme,
      List<Field> fields,
      JourneyBranding branding) {
    public Definition(
        String slug,
        JourneyService.Kind kind,
        String title,
        String description,
        String theme,
        List<Field> fields) {
      this(slug, kind, title, description, theme, fields, null);
    }
  }

  public record Created(Definition site, String managementKey) {}

  public record Availability(String status) {}

  @Transactional(readOnly = true)
  public Availability availability(String slug) {
    return new Availability(addresses.availability(slug));
  }

  private final JourneySiteRepository sites;
  private final ObjectMapper mapper;
  private final ru.ticket.workflow.SiteAddressService addresses;
  private final PublicationAccess access;

  public JourneySiteService(
      JourneySiteRepository sites,
      ObjectMapper mapper,
      ru.ticket.workflow.SiteAddressService addresses,
      PublicationAccess access) {
    this.sites = sites;
    this.mapper = mapper;
    this.addresses = addresses;
    this.access = access;
  }

  @Transactional(readOnly = true)
  public Definition get(String slug) {
    try {
      return mapper.readValue(
          sites.findById(slug).orElseThrow(() -> ApiException.notFound("Site")).definition,
          Definition.class);
    } catch (java.io.IOException error) {
      throw new IllegalStateException(error);
    }
  }

  @Transactional
  public Created create(Definition definition) {
    return create(definition, null);
  }

  @Transactional
  public Created create(Definition definition, String ownerId) {
    validate(definition);
    addresses.reserve(definition.slug(), "site");
    if (sites.existsById(definition.slug()))
      throw ApiException.conflict("This site address is already taken");
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var site = new JourneySite();
    site.slug = definition.slug();
    site.ownerId = ownerId;
    site.definition = json(definition);
    site.managementHash = hash(key);
    site.updatedAt = Instant.now();
    sites.save(site);
    return new Created(definition, key);
  }

  @Transactional
  public Definition update(String slug, String key, Definition definition) {
    var site = sites.findById(slug).orElseThrow(() -> ApiException.notFound("Site"));
    if (key == null
        || !MessageDigest.isEqual(
            site.managementHash.getBytes(StandardCharsets.US_ASCII),
            hash(key).getBytes(StandardCharsets.US_ASCII)))
      throw ApiException.forbidden("Invalid site management key");
    return updateDefinition(site, definition);
  }

  @Transactional
  public Definition updateOwned(String slug, String owner, Definition definition) {
    return updateDefinition(access.site(slug, owner), definition);
  }

  private Definition updateDefinition(JourneySite site, Definition definition) {
    validate(definition);
    String slug = site.slug;
    if (!slug.equals(definition.slug()))
      throw ApiException.badRequest("The published address cannot change");
    var old = get(slug);
    if (old.kind() != definition.kind())
      throw ApiException.badRequest("The published workflow cannot change");
    site.definition = json(definition);
    site.updatedAt = Instant.now();
    sites.save(site);
    return definition;
  }

  public void validateData(String slug, JourneyService.Kind kind, Map<String, String> data) {
    var definition = get(slug);
    if (definition.kind() != kind)
      throw ApiException.badRequest("The document does not match the site workflow");
    for (var field : definition.fields())
      if (field.required() && data.getOrDefault(field.name(), "").isBlank())
        throw ApiException.badRequest("Complete: " + field.label());
  }

  private static void validate(Definition site) {
    if (site != null) JourneyBranding.validate(site.branding());
    if (site == null
        || site.slug() == null
        || !site.slug().matches("[a-z][a-z0-9-]{1,46}[a-z0-9]")
        || site.kind() == null
        || site.title() == null
        || site.title().isBlank()
        || site.title().length() > 120
        || site.description() == null
        || site.description().length() > 600
        || !Set.of("sage", "rose", "ink").contains(site.theme())
        || site.fields() == null
        || site.fields().isEmpty()
        || site.fields().size() > 12)
      throw ApiException.badRequest("Check the site title, address and fields");
    var names = new HashSet<String>();
    if (Set.of(
                "www", "api", "drawio", "go", "mail", "admin", "app", "auth", "static", "cdn",
                "assets", "status", "support", "help")
            .contains(site.slug())
        || Arrays.stream(JourneyService.Kind.values())
            .anyMatch(kind -> kind.slug().equals(site.slug())))
      throw ApiException.badRequest("This subdomain is reserved for CoDoc");
    for (var field : site.fields())
      if (field == null
          || field.name() == null
          || !field.name().matches("[a-z][a-z0-9_]{0,31}")
          || Set.of(
                  "site",
                  "theme",
                  "branding",
                  "route",
                  "route_labels",
                  "locale",
                  "field_labels",
                  "print_width",
                  "print_height",
                  "__proto__",
                  "constructor",
                  "prototype")
              .contains(field.name())
          || !names.add(field.name())
          || field.label() == null
          || field.label().isBlank()
          || field.label().length() > 80
          || !Set.of("text", "textarea", "date", "time").contains(field.type()))
        throw ApiException.badRequest("Invalid form field");
  }

  static void validatePreview(Definition site) {
    validate(
        new Definition(
            "preview-site",
            site.kind(),
            site.title(),
            site.description(),
            site.theme(),
            site.fields(),
            site.branding()));
  }

  private String json(Definition definition) {
    try {
      return mapper.writeValueAsString(definition);
    } catch (java.io.IOException error) {
      throw new IllegalArgumentException(error);
    }
  }

  private static String hash(String key) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }
}
