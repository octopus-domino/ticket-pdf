package ru.ticket.recognition;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.ticket.common.ApiException;
import ru.ticket.common.HeavyOperationCapacity;

/** Rasterizes one explicitly selected page; the existing OCR pipeline receives those same pixels. */
@RestController
@RequestMapping({"/api/recognition/pdf-page", "/api/guest/recognition/pdf-page"})
public class PdfPageController {
  private final HeavyOperationCapacity capacity;
  public PdfPageController(HeavyOperationCapacity capacity) { this.capacity = capacity; }
  public record Request(@NotBlank @Size(max = 11_200_000) String pdf, @Min(1) @Max(500) int page) {}
  public record Page(String image, int pages) {}

  @PostMapping
  public Page render(@Valid @RequestBody Request request) {
    if (!capacity.tryAcquire()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "recognition.busy");
    BufferedImage image = null;
    try {
      byte[] bytes = Base64.getDecoder().decode(request.pdf());
      if (bytes.length > 8 * 1024 * 1024) throw new IOException("PDF size");
      try (var document = Loader.loadPDF(bytes)) {
        if (document.isEncrypted() || document.getNumberOfPages() > 500 || request.page() < 1 || request.page() > document.getNumberOfPages())
          throw new IOException("PDF pages");
        var box = document.getPage(request.page() - 1).getCropBox();
        double width = box.getWidth(), height = box.getHeight();
        if (!Double.isFinite(width) || !Double.isFinite(height) || width < 1 || height < 1)
          throw new IOException("PDF dimensions");
        float scale = (float) Math.min(2, Math.min(4096 / Math.max(width, height), Math.sqrt(4_000_000 / (width * height))));
        var renderer = new PDFRenderer(document);
        renderer.setSubsamplingAllowed(true);
        image = renderer.renderImage(request.page() - 1, scale, ImageType.RGB);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        if (output.size() > 8 * 1024 * 1024) throw new IOException("Rendered page size");
        return new Page("data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray()), document.getNumberOfPages());
      }
    } catch (IOException | IllegalArgumentException ex) {
      throw ApiException.badRequest("recognition.invalid_pdf");
    } finally {
      if (image != null) image.flush();
      capacity.release();
    }
  }
}
