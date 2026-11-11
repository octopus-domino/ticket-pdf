package ru.ticket.journey;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/journey-sites")
public class JourneySiteController {
  private final JourneySiteService service;

  public JourneySiteController(JourneySiteService service) {
    this.service = service;
  }

  @GetMapping("/{slug}")
  public JourneySiteService.Definition get(@PathVariable String slug) {
    return service.get(slug);
  }

  @GetMapping("/availability")
  public JourneySiteService.Availability availability(@RequestParam String slug) {
    return service.availability(slug);
  }

  @PostMapping
  public JourneySiteService.Created create(@RequestBody JourneySiteService.Definition definition) {
    return service.create(definition);
  }

  @PutMapping("/{slug}")
  public JourneySiteService.Definition update(
      @PathVariable String slug,
      @RequestHeader(value = "X-Management-Key", required = false) String key,
      @RequestBody JourneySiteService.Definition definition) {
    return service.update(slug, key, definition);
  }
}
