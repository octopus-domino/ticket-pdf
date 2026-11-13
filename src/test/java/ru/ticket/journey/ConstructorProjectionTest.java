package ru.ticket.journey;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.ticket.dto.*;

class ConstructorProjectionTest {
  private final ObjectMapper mapper = new ObjectMapper();
  @Test void everyReadySolutionAndThemeRetainsItsExactNativeLayout() {
    var factory = new JourneyDocumentFactory("codoc.site", org.mockito.Mockito.mock(JourneySiteService.class), "https://www.codoc.site");
    for (var kind : JourneyService.Kind.values()) for (var theme : List.of("sage", "rose", "ink")) {
      var data = new LinkedHashMap<String,String>();
      data.put("theme", theme); data.put("recipient", "Мария Анна"); data.put("issuer", "CoDoc");
      data.put("description", "Тёплая встреча & добрые воспоминания"); data.put("date", "2100-01-01");
      data.put("time", "12:30"); data.put("location", "Большая улица, 12"); data.put("amount", "1000");
      data.put("price", "1234.50"); data.put("currency", "RUB"); data.put("role", "Гость");
      data.put("valid_until", "2100-02-01"); data.put("contact", "+7 000 000-00-00");
      data.put("positions", "[{\"name\":\"Позиция\",\"quantity\":\"2\",\"price\":\"1234.50\"}]");
      data.put("events", "[{\"id\":\"lesson-1\",\"title\":\"Встреча\",\"date\":\"2100-01-01\",\"time\":\"12:00\",\"location\":\"Онлайн\",\"durationMinutes\":60}]");
      var view = new JourneyService.View("Abc123Def456", kind, "Праздник & встреча", data, java.time.Instant.EPOCH);
      var before = factory.prepare(view, null);
      var packet = JourneyConstructorSource.project(before, data);
      var after = packet.materialize();
      assertThat(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(after)).as(kind + " " + theme).isEqualTo(mapper.valueToTree(before));
      assertThat(packet.template().bindings()).as(kind + " " + theme).isNotEmpty();
    }
  }
  @Test void retainsGeometryRichFormattingImagesQrAndLiteralBracesWhileActuallyBindingTheModel() {
    var request = new ExportRequest(); var slide = new Slide(); slide.id = "one";
    slide.backgroundImage = "data:image/png;base64,background";
    var text = new TextElement(); text.id = "name"; text.type = "text"; text.x = 32; text.y = 44;
    text.text = "Мария & {{literal}}"; text.html = "<p style=\"font-size:24px\">Мария &amp; {{literal}}</p>";
    var image = new ImageElement(); image.id = "picture"; image.type = "image"; image.src = "data:image/png;base64,picture";
    var qr = new QrElement(); qr.id = "qr"; qr.type = "qr"; qr.text = "https://codoc.site/d/Abc123Def456";
    slide.elements.addAll(List.of(text, image, qr)); request.slides = List.of(slide);
    var packet = JourneyConstructorSource.project(request, Map.of("recipient", text.text));
    assertThat(((TextElement)packet.template().slides().get(0).elements.get(0)).text).isEqualTo("{{fields.recipient}}");
    var materialized = packet.materialize();
    assertThat(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(materialized)).isEqualTo(mapper.valueToTree(request));
    var changed = new LinkedHashMap<String,Object>(packet.model()); changed.put("fields", Map.of("recipient", "Анна <Л>"));
    var result = new JourneyConstructorSource.Packet(packet.template(), changed, packet.options()).materialize();
    assertThat(((TextElement)result.slides.get(0).elements.get(0)).text).isEqualTo("Анна <Л>");
    assertThat(((TextElement)result.slides.get(0).elements.get(0)).html).contains("Анна &lt;Л&gt;");
    assertThat(result.slides.get(0).elements.get(0).x).isEqualTo(32);
    assertThat(((TextElement)request.slides.get(0).elements.get(0)).text).isEqualTo("Мария & {{literal}}");
  }
}
