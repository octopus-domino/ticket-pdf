package ru.ticket.workflow;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;
import ru.ticket.journey.JourneyService;

/** One unique namespace shared by simple sites and professional workflows. */
@Service
public class SiteAddressService {
  private final SiteAddressRepository addresses;

  public SiteAddressService(SiteAddressRepository addresses) {
    this.addresses = addresses;
  }

  @Transactional(readOnly = true)
  public String availability(String slug) {
    if (slug == null || !slug.matches("[a-z][a-z0-9-]{1,46}[a-z0-9]")) return "invalid";
    if (Set.of(
                "www", "api", "drawio", "go", "mail", "admin", "app", "auth", "static", "cdn",
                "assets", "status", "support", "help")
            .contains(slug)
        || Arrays.stream(JourneyService.Kind.values()).anyMatch(k -> k.slug().equals(slug)))
      return "reserved";
    return addresses.existsById(slug) ? "taken" : "available";
  }

  @Transactional
  public void reserve(String slug, String kind) {
    if (!"available".equals(availability(slug)))
      throw ApiException.conflict("This site address is already taken");
    var address = new SiteAddress();
    address.slug = slug;
    address.kind = kind;
    addresses.saveAndFlush(address);
  }
}
