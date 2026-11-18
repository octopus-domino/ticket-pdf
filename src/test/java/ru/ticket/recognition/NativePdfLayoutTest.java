package ru.ticket.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.rendering.*;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.layout.*;
import ru.ticket.recognition.style.*;

class NativePdfLayoutTest {
  private final PageLayoutAnalyzer analyzer = new PageLayoutAnalyzer(new RuledTableDetector(),
      new TextElementBuilder(new StableLineRunSplitter()),
      new ArtworkDetector());

  @Test void nativeTextAndTwoIndependentTablesRemainEditableWithoutRasterCopies() throws Exception {
    try (var doc = new PDDocument()) {
      var page = new PDPage(); doc.addPage(page);
      try (var content = new PDPageContentStream(doc, page)) {
        for (int top : new int[]{700, 550}) {
          for (int row = 0; row < 3; row++) {
            content.moveTo(40, top - row * 30); content.lineTo(340, top - row * 30); content.stroke();
          }
          for (int x : new int[]{40, 140, 340}) {
            content.moveTo(x, top); content.lineTo(x, top - 60); content.stroke();
          }
          content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
          content.newLineAtOffset(50, top - 20); content.showText("Editable value"); content.endText();
        }
      }
      var image = new PDFRenderer(doc).renderImage(0, 2, ImageType.RGB);
      var words = new PdfTextExtractor().extract(doc, 1, 2);
      var draft = analyzer.analyze(image, words);
      assertThat(draft.elements().stream().filter(i -> i.type().equals("table"))).hasSize(2);
      assertThat(draft.elements().stream().filter(i -> i.type().equals("image"))).isEmpty();
      assertThat(words).allSatisfy(w -> assertThat(w.hints().fontSize()).isEqualTo(20));
      image.flush();
    }
  }

  /** Manual regression: private samples stay outside Git and their text is never printed. */
  @Test void inspectLocalDocumentWhenExplicitlyProvided() throws Exception {
    String sample = System.getProperty("recognition.sample");
    org.junit.jupiter.api.Assumptions.assumeTrue(sample != null);
    try (var document = Loader.loadPDF(Path.of(sample).toFile())) {
      var image = new PDFRenderer(document).renderImage(0, 3, ImageType.RGB);
      var words = new PdfTextExtractor().extract(document, 1, 3);
      var draft = analyzer.analyze(image, words);
      Path output = Path.of(System.getProperty("recognition.output", "target/native-draft.json"));
      new com.fasterxml.jackson.databind.ObjectMapper().writeValue(output.toFile(), draft);
      System.out.println("native-layout words=" + words.size() + " tables="
          + draft.elements().stream().filter(i -> i.type().equals("table")).count() + " images="
          + draft.elements().stream().filter(i -> i.type().equals("image")).count());
      assertThat(words).isNotEmpty();
      assertThat(draft.elements()).isNotEmpty();
      image.flush();
    }
  }
}
