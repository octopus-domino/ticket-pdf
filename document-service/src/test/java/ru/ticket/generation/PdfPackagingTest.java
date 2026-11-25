package ru.ticket.generation;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import ru.ticket.export.ExportFormat;
import ru.ticket.generation.application.dto.NamedDocument;
import ru.ticket.generation.infrastructure.MultiPagePdfPackager;
import ru.ticket.pdf.PdfASvgToPdfConverter;

class PdfPackagingTest {
  @Test
  void mergedPdfAKeepsPagesOutputIntentAndConformanceMetadata() throws Exception {
    var converter = new PdfASvgToPdfConverter();
    String svg =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"80\"><rect width=\"100\""
            + " height=\"80\" fill=\"#ffffff\"/><circle cx=\"40\" cy=\"40\" r=\"20\""
            + " fill=\"#123456\"/></svg>";
    byte[] source = converter.convert(svg, 100, 80);
    var packager = new MultiPagePdfPackager(converter);
    var result =
        packager.pack(
            List.of(
                new NamedDocument("first.pdf", source), new NamedDocument("second.pdf", source)),
            ExportFormat.PDFA);
    assertEquals("application/pdf", result.mimeType());
    try (var pdf = Loader.loadPDF(result.content())) {
      assertEquals(2, pdf.getNumberOfPages());
      assertFalse(pdf.getDocumentCatalog().getOutputIntents().isEmpty());
      try (var stream = pdf.getDocumentCatalog().getMetadata().exportXMPMetadata()) {
        String xmp = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(xmp.contains("<pdfaid:part>2</pdfaid:part>"));
        assertTrue(xmp.contains("<pdfaid:conformance>B</pdfaid:conformance>"));
      }
    }
  }
}
