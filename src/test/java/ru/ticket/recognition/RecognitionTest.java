package ru.ticket.recognition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.ticket.common.ApiException;

class RecognitionTest {
  @Test void parsesOnlyWordRowsAndPreservesLineIdentity() {
    String tsv = "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n"
        + "4\t1\t1\t1\t1\t0\t0\t0\t100\t20\t-1\t\n"
        + "5\t1\t1\t1\t1\t1\t10\t20\t90\t18\t92.5\tCertificate\n";
    var words = TesseractOcrEngine.parse(tsv);
    assertEquals(1, words.size()); assertEquals("Certificate", words.get(0).text());
    assertEquals(92.5, words.get(0).confidence());
  }
  private BufferedImage paper() {
    var image = new BufferedImage(600, 500, BufferedImage.TYPE_INT_RGB);
    var g = image.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0, 0, 600, 500); g.dispose(); return image;
  }
  @Test void reconstructsRuledTableAsOneNativeTableAndKeepsOtherText() {
    var image = paper(); var g = image.createGraphics(); g.setColor(Color.BLACK);
    for (int y : new int[]{100, 160, 220}) g.drawLine(50, y, 550, y);
    for (int x : new int[]{50, 300, 550}) g.drawLine(x, 100, x, 220);
    g.dispose();
    var words = List.of(new OcrEngine.Word("Invoice", 50, 30, 100, 20, 95, "title"),
        new OcrEngine.Word("Description", 60, 120, 100, 20, 95, "a"),
        new OcrEngine.Word("Total", 310, 120, 80, 20, 95, "b"),
        new OcrEngine.Word("Service", 60, 180, 80, 20, 95, "c"),
        new OcrEngine.Word("100", 310, 180, 40, 20, 95, "d"));
    var draft = new PageLayoutAnalyzer().analyze(image, words);
    var table = draft.elements().stream().filter(a -> a.type().equals("table")).findFirst().orElseThrow();
    assertEquals(List.of(List.of("Description", "Total"), List.of("Service", "100")), table.properties().get("data"));
    assertEquals(1, draft.elements().stream().filter(a -> a.type().equals("text")).count());
    assertTrue(draft.elements().stream().noneMatch(a -> a.type().equals("image")));
  }
  @Test void preservesArtworkAsACropAndWarnsAboutLowConfidence() {
    var image = paper(); var g = image.createGraphics(); g.setColor(Color.RED); g.fillOval(300, 300, 100, 100); g.dispose();
    var draft = new PageLayoutAnalyzer().analyze(image, List.of(new OcrEngine.Word("Maybe", 20, 20, 100, 30, 40, "line")));
    assertTrue(draft.elements().stream().anyMatch(a -> a.type().equals("image") && a.properties().get("src").toString().startsWith("data:image/png;base64,")));
    assertTrue(draft.warnings().contains("recognition.low_confidence"));
  }
  @Test void validatesImageBeforeStartingOcrAndReleasesCapacityAfterFailure() {
    var engine = mock(OcrEngine.class);
    var vision = new LocalVisionRecognizer(new ObjectMapper(), false, "http://127.0.0.1:11434", "qwen3-vl:4b");
    var service = new ImageRecognitionService(engine, new PageLayoutAnalyzer(), vision);
    assertThrows(ApiException.class, () -> service.recognize("data:image/svg+xml;base64,AAAA", "eng", "ocr"));
    verifyNoInteractions(engine);
    when(engine.recognize(any(), eq("eng"))).thenReturn(List.of(new OcrEngine.Word("Hello", 20, 20, 80, 20, 95, "one")));
    var draft = service.recognize(PageLayoutAnalyzer.png(paper()), "eng", "ocr");
    assertEquals("Hello", draft.elements().get(0).properties().get("text"));
    assertThrows(ApiException.class, () -> service.recognize(PageLayoutAnalyzer.png(paper()), "eng;curl", "ocr"));
  }
  @Test void modelOutputCannotSupplyExternalImageUrlsOrOutOfBoundsObjects() throws Exception {
    var mapper = new ObjectMapper();
    var vision = new LocalVisionRecognizer(mapper, false, "http://127.0.0.1:11434", "qwen3-vl:4b");
    var draft = vision.parse(mapper.readTree("{\"elements\":[{\"type\":\"image\",\"x\":20,\"y\":20,\"width\":100,\"height\":100,\"properties\":{\"src\":\"https://external.test/track\"}}]}"), paper());
    assertTrue(draft.elements().get(0).properties().get("src").toString().startsWith("data:image/png;base64,"));
    assertThrows(ApiException.class, () -> vision.parse(mapper.readTree("{\"elements\":[{\"type\":\"text\",\"x\":590,\"y\":20,\"width\":100,\"height\":100,\"properties\":{}}]}"), paper()));
  }
}
