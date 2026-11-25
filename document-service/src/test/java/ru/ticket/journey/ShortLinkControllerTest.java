package ru.ticket.journey;

import ru.ticket.journey.application.journey.JourneyService;
import ru.ticket.journey.application.journey.JourneySiteService;
import ru.ticket.journey.web.journey.ShortLinkController;
import ru.ticket.journey.infrastructure.pdf.JourneyDocumentFactory;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShortLinkControllerTest {
  @Test
  void shortLinkResolvesOnlyAnExistingDocumentAndUsesTheSiteDomain() {
    var service = mock(JourneyService.class);
    when(service.get("Abc123Def456"))
        .thenReturn(
            new JourneyService.View(
                "Abc123Def456",
                JourneyService.Kind.MENU,
                "Кафе",
                Map.of("site", "my-cafe"),
                Instant.now()));
    var controller =
        new ShortLinkController(
            service, new JourneyDocumentFactory("codoc.site", mock(JourneySiteService.class)));
    assertThat(controller.redirect("Abc123Def456").getHeaders().getLocation())
        .hasToString("https://my-cafe.codoc.site/d/Abc123Def456");
    assertThatThrownBy(() -> controller.redirect("https://example.com"))
        .isInstanceOf(ru.ticket.common.ApiException.class);
  }
}
