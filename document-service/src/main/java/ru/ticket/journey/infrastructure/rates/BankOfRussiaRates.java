package ru.ticket.journey.infrastructure.rates;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/** Caches the official Bank of Russia daily reference rates. */
@Component
@ConditionalOnProperty(name = "app.journeys.trip-planner-enabled", havingValue = "true")
public class BankOfRussiaRates {
  private static final int MAX_BYTES = 256_000;
  private Snapshot cached;
  private Instant retryAt = Instant.EPOCH;

  /** Rubles per single unit of each listed currency, including RUB itself. */
  public record Snapshot(LocalDate date, Map<String, BigDecimal> rates) {}

  /** Serves cached data while respecting a bounded refresh interval. */
  public synchronized Snapshot current() {
    Instant now = Instant.now();
    if (!now.isBefore(retryAt)) {
      retryAt = now.plus(Duration.ofMinutes(2));
      try {
        cached = fetch();
        retryAt = now.plus(Duration.ofHours(1));
      } catch (IOException unavailable) {
        // Keep the last dated snapshot available when the upstream cannot be reached.
      }
    }
    return cached;
  }

  private static Snapshot fetch() throws IOException {
    var connection = (HttpURLConnection) URI.create("https://www.cbr.ru/scripts/XML_daily.asp")
        .toURL().openConnection();
    connection.setConnectTimeout(3000);
    connection.setReadTimeout(5000);
    connection.setInstanceFollowRedirects(false);
    try {
      if (connection.getResponseCode() != 200) throw new IOException("Exchange rates unavailable");
      try (var stream = connection.getInputStream()) {
        byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IOException("Exchange rate response too large");
        return parse(bytes);
      }
    } finally {
      connection.disconnect();
    }
  }

  static Snapshot parse(byte[] bytes) throws IOException {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      var root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
      if (!"ValCurs".equals(root.getTagName())) throw new IOException("Invalid rate response");
      LocalDate date = LocalDate.parse(root.getAttribute("Date"), DateTimeFormatter.ofPattern("dd.MM.uuuu"));
      var rates = new LinkedHashMap<String, BigDecimal>();
      rates.put("RUB", BigDecimal.ONE);
      var values = root.getElementsByTagName("Valute");
      for (int index = 0; index < values.getLength(); index++) {
        var value = (Element) values.item(index);
        String code = text(value, "CharCode");
        var nominal = new BigDecimal(text(value, "Nominal"));
        var amount = new BigDecimal(text(value, "Value").replace(',', '.'));
        if (code.matches("[A-Z]{3}") && nominal.signum() > 0 && amount.signum() > 0) {
          rates.putIfAbsent(code, amount.divide(nominal, MathContext.DECIMAL128));
        }
      }
      if (rates.size() < 2) throw new IOException("No exchange rates");
      return new Snapshot(date, Map.copyOf(rates));
    } catch (ParserConfigurationException | SAXException | RuntimeException invalid) {
      throw new IOException("Invalid exchange rate response", invalid);
    }
  }

  private static String text(Element element, String tag) {
    return element.getElementsByTagName(tag).item(0).getTextContent().strip();
  }
}
