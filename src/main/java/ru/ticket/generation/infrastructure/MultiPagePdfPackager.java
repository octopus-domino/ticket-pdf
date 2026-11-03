package ru.ticket.generation.infrastructure;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.stereotype.Component;
import ru.ticket.generation.application.dto.*;
import ru.ticket.generation.application.port.DocumentPackager;

@Component
public class MultiPagePdfPackager implements DocumentPackager {
  private final ru.ticket.pdf.PdfASvgToPdfConverter pdfA;

  public MultiPagePdfPackager(ru.ticket.pdf.PdfASvgToPdfConverter pdfA) {
    this.pdfA = pdfA;
  }

  public String packAs() {
    return "multipage-pdf";
  }

  public String mimeType() {
    return "application/pdf";
  }

  public String extension() {
    return ".pdf";
  }

  public PackResult pack(List<NamedDocument> docs) {
    return pack(docs, ru.ticket.export.ExportFormat.PDF);
  }

  @Override
  public PackResult pack(List<NamedDocument> docs, ru.ticket.export.ExportFormat format) {
    try {
      var merger = new PDFMergerUtility();
      var out = new ByteArrayOutputStream();
      merger.setDestinationStream(out);
      for (var doc : docs) merger.addSource(new RandomAccessReadBuffer(doc.content()));
      merger.mergeDocuments(null);
      byte[] result = out.toByteArray();
      if (format == ru.ticket.export.ExportFormat.PDFA) result = pdfA.applyPdfAMetadata(result);
      return new PackResult(result, "documents.pdf", mimeType());
    } catch (Exception error) {
      throw new IllegalArgumentException("PDF merge failed", error);
    }
  }
}
