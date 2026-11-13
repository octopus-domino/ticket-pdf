package ru.ticket.journey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/** Browser Maps keys are domain-restricted public credentials; server-only POI keys are never exposed. */
@RestController
@RequestMapping("/api/journeys/maps")
public class MapProvidersController {
  private final String key;
  public MapProvidersController(@Value("${app.journeys.yandex-api-key:}") String key){this.key=key.strip();}
  public record Providers(boolean yandexEnabled,String yandexBrowserKey) {}
  @GetMapping("/providers") public Providers providers(){return new Providers(!key.isBlank(),key.isBlank()?null:key);}
}
