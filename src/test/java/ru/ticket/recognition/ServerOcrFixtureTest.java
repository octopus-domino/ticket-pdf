package ru.ticket.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.ensemble.*;
import ru.ticket.recognition.layout.*;
import ru.ticket.recognition.style.*;

/** Replays actual server OCR without copying private samples or recognized personal fields into Git. */
class ServerOcrFixtureTest {
  @Test void serverResultsReconstructEditableTablesAndConsistentText() throws Exception {
    String directory = System.getProperty("recognition.ocrFixtures");
    org.junit.jupiter.api.Assumptions.assumeTrue(directory != null);
    var root = Path.of(directory);
    var analyzer = new PageLayoutAnalyzer(new RuledTableDetector(), new TextElementBuilder(new StableLineRunSplitter()), new ArtworkDetector());
    for (int sample = 1; sample <= 2; sample++) {
      var primary = TesseractOcrEngine.parse(Files.readString(root.resolve("ocr-" + sample + "-psm3.tsv")));
      var sparse = TesseractOcrEngine.parse(Files.readString(root.resolve("ocr-" + sample + "-psm11.tsv")));
      var words = OcrLineReconciler.reconcile(new OcrResultFusion().fuse(primary, sparse), primary);
      var image = ImageIO.read(root.resolve("ocr-input-" + sample + ".png").toFile());
      var draft = analyzer.analyze(image, words);
      new com.fasterxml.jackson.databind.ObjectMapper().writeValue(root.resolve("server-ocr-draft-" + sample + ".json").toFile(), draft);
      long tables = draft.elements().stream().filter(i -> i.type().equals("table")).count();
      System.out.println("server-ocr sample=" + sample + " words=" + words.size() + " tables=" + tables);
      assertThat(tables).isPositive();
      assertThat(draft.elements().stream().filter(i -> i.type().equals("text")))
          .allSatisfy(item -> assertThat(item.properties().get("richText")).isEqualTo(false));
      image.flush();
    }
  }
}
