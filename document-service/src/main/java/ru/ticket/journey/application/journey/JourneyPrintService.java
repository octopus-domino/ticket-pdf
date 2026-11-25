package ru.ticket.journey.application.journey;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;
import ru.ticket.export.ExportFacade;
import ru.ticket.export.ExportFormat;
import ru.ticket.generation.application.GenerationService;
import ru.ticket.generation.application.dto.NamedDocument;
import ru.ticket.journey.domain.model.Trip;
import ru.ticket.journey.infrastructure.pdf.JourneyDocumentFactory;

/** Prepares published documents and releases for rendering under a shared capacity limit. */
@Service
public class JourneyPrintService {
  private final JourneyService publications;
  private final JourneyDocumentFactory documents;
  private final ExportFacade export;
  private final GenerationService generation;
  private final Semaphore preparation = new Semaphore(1);

  /** Wires publication lookup, document layout, rendering and packaging. */
  public JourneyPrintService(JourneyService publications, JourneyDocumentFactory documents,
      ExportFacade export, GenerationService generation) {
    this.publications = publications;
    this.documents = documents;
    this.export = export;
    this.generation = generation;
  }

  /** Renders a published document as standard or archival PDF. */
  public byte[] document(String id, boolean archive) {
    return prepare(() -> {
      long started = System.nanoTime();
      var view = publications.get(id);
      var document = documents.document(view);
      long prepared = System.nanoTime();
      byte[] content = export.export(archive ? ExportFormat.PDFA : ExportFormat.PDF, document).content();
      LoggerFactory.getLogger(getClass()).info("journey.pdf id={} kind={} prepare_ms={} render_ms={} bytes={}",
          id, view.kind(), (prepared - started) / 1_000_000, (System.nanoTime() - prepared) / 1_000_000, content.length);
      return content;
    });
  }

  /** Renders a private trip through the same bounded PDF pipeline. */
  public byte[] tripPdf(Trip trip) {
    return prepare(() -> export.export(ExportFormat.PDF, documents.trip(trip)).content());
  }

  /** Renders non-revoked documents in an authorized release as a single PDF. */
  public byte[] batchPdf(JourneyService.Batch batch) {
    return prepare(() -> {
      var values = batch.documents().stream().filter(JourneyPrintService::active)
          .map(JourneyService.Issued::document).toList();
      return export.export(ExportFormat.PDF, documents.batch(values)).content();
    });
  }

  /** Packs individually named PDFs from an authorized release into a bounded ZIP. */
  public byte[] zip(JourneyService.Batch batch) {
    return prepare(() -> {
      var files = new ArrayList<NamedDocument>();
      long bytes = 0;
      long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
      for (var item : batch.documents()) {
        if (!active(item)) continue;
        if (System.nanoTime() > deadline) throw ApiException.badRequest("Release is too large; split the release");
        var pdf = export.export(ExportFormat.PDF, documents.document(item.document()));
        bytes += pdf.content().length;
        if (bytes > 32L * 1024 * 1024) throw ApiException.badRequest("Release exceeds 32 MiB; split the release");
        files.add(new NamedDocument("codoc-" + item.document().id() + ".pdf", pdf.content()));
      }
      return generation.pack("zip", "pdf", files).content();
    });
  }

  private static boolean active(JourneyService.Issued item) {
    return !"REVOKED".equals(item.status().state());
  }

  private byte[] prepare(Supplier<byte[]> action) {
    if (!preparation.tryAcquire())
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Another PDF is being prepared. Please retry shortly.");
    try {
      return action.get();
    } finally {
      preparation.release();
    }
  }
}
