package ru.ticket.recognition;

import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import ru.ticket.common.HeavyOperationCapacity;

class PdfPageControllerTest {
  private final HeavyOperationCapacity capacity = new HeavyOperationCapacity();
  private final PdfPageController controller = new PdfPageController(capacity);
  private String pdf() throws Exception {
    try (var doc = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
      doc.addPage(new PDPage(new PDRectangle(300, 200)));
      doc.addPage(new PDPage(new PDRectangle(200, 300)));
      doc.save(bytes); return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
  }
  @Test void rendersTheSelectedPageAndPreservesItsOrientation() throws Exception {
    var result = controller.render(new PdfPageController.Request(pdf(), 2));
    assertThat(result.pages()).isEqualTo(2);
    var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(result.image().split(",")[1])));
    assertThat(image.getHeight()).isGreaterThan(image.getWidth());
    image.flush();
  }
  @Test void rejectsInvalidDataAndReleasesCapacity() throws Exception {
    assertThatThrownBy(() -> controller.render(new PdfPageController.Request("not-pdf", 1))).hasMessageContaining("recognition.invalid_pdf");
    assertThatThrownBy(() -> controller.render(new PdfPageController.Request(pdf(), 3))).hasMessageContaining("recognition.invalid_pdf");
    assertThat(controller.render(new PdfPageController.Request(pdf(), 1)).pages()).isEqualTo(2);
  }
  @Test void sharesAdmissionWithOtherHeavyOperations() throws Exception {
    assertThat(capacity.tryAcquire()).isTrue();
    try { assertThatThrownBy(() -> controller.render(new PdfPageController.Request(pdf(), 1))).hasMessageContaining("recognition.busy"); }
    finally { capacity.release(); }
  }
}
