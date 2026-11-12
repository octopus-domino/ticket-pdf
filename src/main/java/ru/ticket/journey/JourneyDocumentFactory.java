package ru.ticket.journey;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.ticket.dto.ExportRequest;
import ru.ticket.dto.QrElement;
import ru.ticket.dto.Slide;
import ru.ticket.dto.TextElement;

/** Projects a workflow document into paginated, printable pages. */
@Component
public class JourneyDocumentFactory {
  private static final double CONTENT_WIDTH = 682;
  private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);
  private static final Font METRIC_FONT = metricFont();
  private final String domain;
  private final JourneySiteService sites;
  private final String frontendUrl;
  private final JourneyBrandingLayout brandingLayout;
  private final JourneyRouteLayout routeLayout;

  @org.springframework.beans.factory.annotation.Autowired
  public JourneyDocumentFactory(
      @Value("${app.journeys.domain:codoc.site}") String domain,
      JourneySiteService sites,
      @Value("${app.frontend-url:https://www.codoc.site}") String frontendUrl,
      JourneyBrandingLayout brandingLayout,
      JourneyRouteLayout routeLayout) {
    this.domain = domain;
    this.sites = sites;
    this.frontendUrl = frontendUrl;
    this.brandingLayout = brandingLayout;
    this.routeLayout = routeLayout;
  }

  public JourneyDocumentFactory(
      String domain,
      JourneySiteService sites,
      String frontendUrl,
      JourneyBrandingLayout brandingLayout) {
    this(domain, sites, frontendUrl, brandingLayout, null);
  }

  public JourneyDocumentFactory(String domain, JourneySiteService sites, String frontendUrl) {
    this(domain, sites, frontendUrl, null);
  }

  public JourneyDocumentFactory(String domain, JourneySiteService sites) {
    this(domain, sites, "https://www." + domain);
  }

  public String publicUrl(JourneyService.View view) {
    var frontend = java.net.URI.create(frontendUrl);
    if (frontend.getHost() != null
        && Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(frontend.getHost()))
      return frontend.getScheme() + "://" + frontend.getRawAuthority() + "/d/" + view.id();
    String site = view.kind().slug();
    String custom = view.data().get("site");
    if (custom != null && custom.matches("[a-z][a-z0-9-]{1,46}[a-z0-9]")) site = custom;
    return "https://" + site + "." + domain + "/d/" + view.id();
  }

  public ExportRequest document(JourneyService.View view) {
    JourneySiteService.Definition definition =
        view.data().containsKey("site") ? sites.get(view.data().get("site")) : null;
    return document(view, definition);
  }

  public ExportRequest document(
      JourneyService.View view, JourneySiteService.Definition definition) {
    var result = compose(view, definition);
    if (view.data().containsKey("route") && !view.data().get("route").isBlank()) {
      if (routeLayout == null) throw new IllegalStateException("Route layout is not configured");
      routeLayout.append(result, view);
    }
    var branding =
        view.data().containsKey("branding")
            ? JourneyBranding.parse(view.data().get("branding"))
            : definition == null ? null : definition.branding();
    if (branding != null && (branding.logo() != null || branding.picture() != null)) {
      if (brandingLayout == null) throw new IllegalStateException("Media layout is not configured");
      brandingLayout.apply(result, branding);
    }
    return result;
  }

  private ExportRequest compose(
      JourneyService.View view, JourneySiteService.Definition definition) {
    if (JourneyGridLayout.supports(view.kind()))
      return new JourneyGridLayout().single(view, publicUrl(view));
    if (view.kind() == JourneyService.Kind.PROPOSAL)
      return new ProposalDocumentLayout().compose(view);
    if (view.kind() == JourneyService.Kind.INVITATION || view.kind() == JourneyService.Kind.TICKET)
      return new EventDocumentLayout().compose(view, publicUrl(view), definition);
    String theme =
        view.data().getOrDefault("theme", definition == null ? "sage" : definition.theme());
    String background =
        switch (theme) {
          case "rose" -> "#fff8ef";
          case "ink" -> "#202e3c";
          default -> "#f8f7ec";
        };
    String foreground =
        switch (theme) {
          case "rose" -> "#7a453d";
          case "ink" -> "#e8dfc8";
          default -> "#284d3b";
        };
    List<String> lines = new ArrayList<>();
    var captions = JourneyFieldLabels.read(view.data().get("field_labels"));
    if (!captions.isEmpty()) {
      captions.forEach(
          (key, label) -> {
            if (!Set.of("recipient", "issuer", "events", "branding", "route").contains(key)
                && !view.data().getOrDefault(key, "").isBlank())
              addLines(lines, label + ": " + view.data().get(key));
          });
      if (view.kind() == JourneyService.Kind.SCHEDULE) {
        var policy = new JourneyContentPolicy(new com.fasterxml.jackson.databind.ObjectMapper());
        for (var lesson : policy.lessons(view.data()))
          addLines(
              lines,
              lesson.date()
                  + " "
                  + lesson.time()
                  + " — "
                  + lesson.title()
                  + " / "
                  + lesson.location());
      }
    } else if (definition == null) {
      for (String key :
          List.of(
              "description",
              "date",
              "time",
              "location",
              "items",
              "contact",
              "role",
              "phone",
              "email",
              "vehicle",
              "amount",
              "valid_until",
              "portfolio")) {
        addLines(lines, view.data().getOrDefault(key, ""));
      }
      if (view.kind() == JourneyService.Kind.SCHEDULE) {
        var policy = new JourneyContentPolicy(new com.fasterxml.jackson.databind.ObjectMapper());
        for (var lesson : policy.lessons(view.data()))
          addLines(
              lines,
              lesson.date()
                  + " "
                  + lesson.time()
                  + " — "
                  + lesson.title()
                  + " / "
                  + lesson.location());
      }
    } else {
      for (var field : definition.fields()) {
        if (List.of("recipient", "issuer").contains(field.name())) continue;
        String value = view.data().getOrDefault(field.name(), "").strip();
        if (!value.isEmpty()) addLines(lines, field.label() + ": " + value);
      }
    }
    if (lines.isEmpty()) lines.add("");
    List<String> titleLines = wrap(view.title(), 30);
    String recipient = view.data().getOrDefault("recipient", "");
    String issuer = view.data().getOrDefault("issuer", "");
    if (issuer.length() > 150) {
      lines.addAll(0, wrap(issuer, 18));
      issuer = "";
    }
    List<String> recipientLines = recipient.isBlank() ? List.of() : wrap(recipient, 26);
    List<String> issuerLines = issuer.isBlank() ? List.of() : wrap(issuer, 18);
    double recipientY = 112 + titleLines.size() * 41 + 24;
    double issuerY = recipientY + recipientLines.size() * 36 + (recipientLines.isEmpty() ? 0 : 20);
    double bodyY = Math.max(320, issuerY + issuerLines.size() * 25 + 24);
    int linesPerPage = Math.max(1, (int) ((900 - bodyY) / 25));
    var request = new ExportRequest();
    var pages = new ArrayList<Slide>();
    for (int offset = 0; offset < lines.size(); offset += linesPerPage) {
      var page = new Slide();
      page.id = view.id() + "-" + pages.size();
      page.name = view.title();
      page.background = background;
      page.elements.add(text("CoDoc", 56, 48, 20, foreground, false, 30));
      page.elements.add(
          text(
              String.join("\n", titleLines),
              56,
              112,
              30,
              foreground,
              true,
              titleLines.size() * 41));
      if (!recipientLines.isEmpty())
        page.elements.add(
            text(
                String.join("\n", recipientLines),
                56,
                recipientY,
                26,
                foreground,
                true,
                recipientLines.size() * 36));
      if (!issuerLines.isEmpty())
        page.elements.add(
            text(
                String.join("\n", issuerLines),
                56,
                issuerY,
                18,
                foreground,
                false,
                issuerLines.size() * 25));
      page.elements.add(
          text(
              String.join(
                  "\n", lines.subList(offset, Math.min(offset + linesPerPage, lines.size()))),
              56,
              bodyY,
              18,
              foreground,
              false,
              900 - bodyY));
      var qr = new QrElement();
      qr.id = "qr";
      qr.type = "qr";
      qr.x = 56;
      qr.y = 940;
      qr.width = 110;
      qr.height = 110;
      qr.text = publicUrl(view);
      page.elements.add(qr);
      page.elements.add(text(JourneyPrintCopy.qr(view), 195, 970, 14, foreground, false, 25));
      page.elements.add(
          text(
              "CoDoc / " + view.id() + " / " + (pages.size() + 1),
              195,
              1005,
              10,
              foreground,
              false,
              20));
      pages.add(page);
    }
    request.slides = pages;
    return request;
  }

  public ExportRequest batch(List<JourneyService.View> values) {
    if (values.isEmpty()) throw ru.ticket.common.ApiException.badRequest("No active documents");
    if (values.stream().allMatch(value -> JourneyGridLayout.supports(value.kind())))
      return new JourneyGridLayout().batch(values, this::document);
    var result = new ExportRequest();
    var pages = new ArrayList<Slide>();
    if (values.stream().allMatch(v -> v.kind() == JourneyService.Kind.BADGE)) {
      for (int index = 0; index < values.size(); index++) {
        if (index % 6 == 0) {
          var page = new Slide();
          page.id = "badges-" + pages.size();
          pages.add(page);
        }
        var view = values.get(index);
        var page = pages.get(pages.size() - 1);
        double x = 40 + (index % 2) * 380;
        double y = 40 + ((index % 6) / 2) * 350;
        page.elements.add(fittedCell(view.title(), x, y, 330, 60, 18));
        page.elements.add(
            fittedCell(view.data().getOrDefault("recipient", ""), x, y + 70, 230, 100, 22));
        page.elements.add(
            fittedCell(view.data().getOrDefault("role", ""), x, y + 180, 230, 70, 14));
        var qr = new QrElement();
        qr.id = view.id();
        qr.type = "qr";
        qr.x = x + 250;
        qr.y = y + 100;
        qr.width = 80;
        qr.height = 80;
        qr.text = publicUrl(view);
        page.elements.add(qr);
        page.elements.add(
            fittedCell(view.data().getOrDefault("issuer", ""), x, y + 260, 330, 50, 14));
      }
    } else {
      for (var view : values) {
        pages.addAll(document(view).slides);
        if (pages.size() > 200)
          throw ru.ticket.common.ApiException.badRequest(
              "Release exceeds 200 pages; split the release");
      }
    }
    result.slides = pages;
    return result;
  }

  private static void addLines(List<String> lines, String value) {
    value.strip().lines().forEach(line -> lines.addAll(wrap(line, 18)));
  }

  private static List<String> wrap(String value, int size) {
    return wrap(value, size, CONTENT_WIDTH);
  }

  private static List<String> wrap(String value, int size, double width) {
    Font font = METRIC_FONT.deriveFont((float) size);
    List<String> result = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    // Code points avoid breaking surrogate pairs in long names or unspaced text.
    for (int codePoint : value.codePoints().toArray()) {
      if (codePoint == '\r') continue;
      if (codePoint == '\n') {
        result.add(line.toString());
        line.setLength(0);
        continue;
      }
      String next = new String(Character.toChars(codePoint));
      if (line.length() > 0 && font.getStringBounds(line + next, METRICS).getWidth() > width - 8) {
        int space = line.lastIndexOf(" ");
        if (space > 0) {
          result.add(line.substring(0, space));
          line.delete(0, space + 1);
        } else {
          result.add(line.toString());
          line.setLength(0);
        }
      }
      line.append(next);
    }
    result.add(line.toString());
    return result;
  }

  private static TextElement text(
      String content, double x, double y, double size, String color, boolean bold, double height) {
    var text = new TextElement();
    text.id = java.util.UUID.randomUUID().toString();
    text.type = "text";
    text.x = x;
    text.y = y;
    text.width = CONTENT_WIDTH;
    text.height = height;
    text.text = content;
    text.fontSize = size;
    text.fill = color;
    text.fontFamily = "DejaVu Sans";
    text.fontStyle = bold ? "bold" : "normal";
    text.lineHeight = 1.35;
    return text;
  }

  private static TextElement cell(
      String content, double x, double y, double width, double height, int size) {
    var value =
        text(String.join("\n", wrap(content, size, width)), x, y, size, "#202e3c", false, height);
    value.width = width;
    return value;
  }

  private static TextElement fittedCell(
      String content, double x, double y, double width, double height, int size) {
    while (size > 8 && wrap(content, size, width).size() * size * 1.35 > height) size--;
    if (wrap(content, size, width).size() * size * 1.35 > height)
      throw ru.ticket.common.ApiException.badRequest(
          "Badge text is too long; shorten the title, name or organization");
    return cell(content, x, y, width, height, size);
  }

  private static Font metricFont() {
    try (var input =
        JourneyDocumentFactory.class.getResourceAsStream("/fonts/DejaVuSans-Bold.ttf")) {
      if (input == null) throw new IllegalStateException("Bundled layout font missing");
      return Font.createFont(Font.TRUETYPE_FONT, input);
    } catch (Exception error) {
      throw new IllegalStateException("Cannot load printable workflow font", error);
    }
  }
}
