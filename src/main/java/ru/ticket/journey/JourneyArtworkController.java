package ru.ticket.journey;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import ru.ticket.common.ApiException;
import ru.ticket.common.HeavyOperationCapacity;
import ru.ticket.svg.SvgDocumentBuilder;

/** Read-only composition: the same slides and SVG builder are used by the PDF exporter. */
@RestController
@RequestMapping("/api/journeys")
public class JourneyArtworkController {
  public record Input(
      String id,
      JourneyService.Kind kind,
      String title,
      Map<String, String> data,
      JourneySiteService.Definition definition) {
    public Input(String id, JourneyService.Kind kind, String title, Map<String, String> data) {
      this(id, kind, title, data, null);
    }
  }

  public record Page(double width, double height, String svg) {}

  public record Artwork(List<Page> pages) {}

  private final JourneyDocumentFactory documents;
  private final SvgDocumentBuilder svg;
  private final HeavyOperationCapacity capacity;

  public JourneyArtworkController(
      JourneyDocumentFactory documents, SvgDocumentBuilder svg, HeavyOperationCapacity capacity) {
    this.documents = documents;
    this.svg = svg;
    this.capacity = capacity;
  }

  @PostMapping("/artwork")
  public ResponseEntity<Artwork> artwork(@RequestBody Input input) {
    if (input == null) throw ApiException.badRequest("Заполните приглашение");
    JourneyService.validate(
        new JourneyService.Input(input.kind(), input.title(), input.data(), List.of()));
    if (input.definition() != null) {
      JourneySiteService.validatePreview(input.definition());
      if (input.definition().kind() != input.kind())
        throw ApiException.badRequest("Invalid preview workflow");
    }
    if (!capacity.tryAcquire())
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, "Предпросмотр занят. Повторите чуть позже.");
    try {
      String id =
          input.id() != null && input.id().matches("[A-Za-z0-9]{12}") ? input.id() : "preview";
      var view =
          new JourneyService.View(id, input.kind(), input.title(), input.data(), Instant.EPOCH);
      var pages =
          (input.definition() == null
                  ? documents.document(view)
                  : documents.document(view, input.definition()))
              .slides.stream()
                  .map(page -> new Page(page.width, page.height, svg.generate(page)))
                  .toList();
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Artwork(pages));
    } finally {
      capacity.release();
    }
  }

  @GetMapping("/fonts/{face}")
  public ResponseEntity<Resource> font(@PathVariable String face) {
    String file =
        switch (face) {
          case "sans" -> "DejaVuSans.ttf";
          case "sans-bold" -> "DejaVuSans-Bold.ttf";
          case "serif" -> "DejaVuSerif.ttf";
          case "serif-italic" -> "DejaVuSerif-Italic.ttf";
          default -> throw ApiException.notFound("Font");
        };
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("font/ttf"))
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePublic())
        .body(new ClassPathResource("fonts/" + file));
  }
}
