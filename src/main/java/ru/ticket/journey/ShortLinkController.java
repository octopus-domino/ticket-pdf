package ru.ticket.journey;

import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import ru.ticket.common.ApiException;

@RestController
@RequestMapping("/api/links")
public class ShortLinkController {
  private final JourneyService documents;
  private final JourneyDocumentFactory factory;

  public ShortLinkController(JourneyService documents, JourneyDocumentFactory factory) {
    this.documents = documents;
    this.factory = factory;
  }

  @GetMapping("/{code}")
  public ResponseEntity<Void> redirect(@PathVariable String code) {
    if (!code.matches("[A-Za-z0-9]{12}")) throw ApiException.notFound("Link");
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(factory.publicUrl(documents.get(code))))
        .cacheControl(CacheControl.noStore())
        .build();
  }
}
