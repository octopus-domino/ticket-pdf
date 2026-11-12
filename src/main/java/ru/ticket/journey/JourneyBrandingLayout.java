package ru.ticket.journey;

import java.util.Base64;
import org.springframework.stereotype.Component;
import ru.ticket.dto.*;

/** Decorates the canonical page model, shared by browser SVG and downloadable PDF. */
@Component
public class JourneyBrandingLayout {
  private final JourneyMediaService media;

  public JourneyBrandingLayout(JourneyMediaService media) {
    this.media = media;
  }

  public void apply(ExportRequest document, JourneyBranding branding) {
    if (branding == null) return;
    JourneyBranding.validate(branding);
    var logo = branding.logo() == null ? null : media.get(branding.logo().mediaId());
    var picture = branding.picture() == null ? null : media.get(branding.picture().mediaId());
    for (int index = 0; index < document.slides.size(); index++) {
      var page = document.slides.get(index);
      if (logo != null) place(page, branding.logo(), logo, "company-logo");
      if (index == 0 && picture != null)
        place(page, branding.picture(), picture, "document-picture");
    }
  }

  private void place(
      Slide page, JourneyBranding.Placement placement, JourneyMedia source, String id) {
    double margin = Math.min(24, Math.min(page.width, page.height) * .04);
    double width = Math.min(page.width - margin * 2, page.width * placement.scale());
    double height = width * source.height / source.width;
    if (height > page.height - margin * 2) {
      height = page.height - margin * 2;
      width = height * source.width / source.height;
    }
    var image = new ImageElement();
    image.id = id;
    image.type = "image";
    image.width = width;
    image.height = height;
    image.x = margin + (page.width - margin * 2 - width) * placement.x();
    image.y = margin + (page.height - margin * 2 - height) * placement.y();
    image.src =
        "data:"
            + source.contentType
            + ";base64,"
            + Base64.getEncoder().encodeToString(source.content);
    page.elements.add(image);
  }
}
