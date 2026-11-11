package ru.ticket.journey;

import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.ticket.auth.security.UserIdPrincipal;

@RestController
@RequestMapping("/api/publications")
public class PublicationsController {
  private final PublicationsService registry;
  private final JourneyService documents;
  private final JourneySiteService sites;

  public PublicationsController(
      PublicationsService registry, JourneyService documents, JourneySiteService sites) {
    this.registry = registry;
    this.documents = documents;
    this.sites = sites;
  }

  @GetMapping
  public PublicationsService.Results<PublicationsService.Document> list(
      @AuthenticationPrincipal UserIdPrincipal user,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "") String site,
      @RequestParam(defaultValue = "") String search) {
    return registry.documents(user.userId(), page, site, search);
  }

  @GetMapping("/sites")
  public PublicationsService.Results<PublicationsService.Site> sites(
      @AuthenticationPrincipal UserIdPrincipal user, @RequestParam(defaultValue = "0") int page) {
    return registry.sites(user.userId(), page);
  }

  @PostMapping("/claim")
  public Map<String, Integer> claim(
      @AuthenticationPrincipal UserIdPrincipal user,
      @RequestBody List<PublicationsService.Claim> claims) {
    return registry.claim(user.userId(), claims);
  }

  @PutMapping("/sites/{slug}")
  public JourneySiteService.Definition updateSite(
      @AuthenticationPrincipal UserIdPrincipal user,
      @PathVariable String slug,
      @RequestBody JourneySiteService.Definition definition) {
    return sites.updateOwned(slug, user.userId(), definition);
  }

  @GetMapping("/{id}")
  public JourneyService.View manage(
      @AuthenticationPrincipal UserIdPrincipal user, @PathVariable String id) {
    return documents.manageOwned(id, user.userId());
  }

  @PutMapping("/{id}")
  public JourneyService.View update(
      @AuthenticationPrincipal UserIdPrincipal user,
      @PathVariable String id,
      @RequestBody JourneyService.Input input) {
    return documents.updateOwned(id, user.userId(), input);
  }

  @DeleteMapping("/{id}")
  public void revoke(@AuthenticationPrincipal UserIdPrincipal user, @PathVariable String id) {
    documents.revokeOwned(id, user.userId());
  }

  @GetMapping("/{id}/responses")
  public List<JourneyService.Guest> guests(
      @AuthenticationPrincipal UserIdPrincipal user, @PathVariable String id) {
    return documents.guestsOwned(id, user.userId());
  }

  @PostMapping("/{id}/consume")
  public JourneyContentPolicy.Status consume(
      @AuthenticationPrincipal UserIdPrincipal user, @PathVariable String id) {
    return documents.consumeOwned(id, user.userId());
  }
}
