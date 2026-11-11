package ru.ticket.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import ru.ticket.common.ApiException;

/** The server calculates money independently from spreadsheet formula caches and browser totals. */
public final class ProposalContent {
  public record Item(String name, String quantity, String price) {
    public BigDecimal total() {
      return decimal(quantity).multiply(decimal(price)).setScale(2, RoundingMode.HALF_UP);
    }
  }

  private ProposalContent() {}

  public static List<Item> read(String json) {
    try {
      var items = List.of(new ObjectMapper().readValue(json, Item[].class));
      if (items.isEmpty() || items.size() > 200) throw new IllegalArgumentException();
      for (var item : items) {
        if (item.name() == null
            || item.name().isBlank()
            || item.name().length() > 200
            || decimal(item.quantity()).signum() <= 0
            || decimal(item.price()).signum() < 0) throw new IllegalArgumentException();
      }
      return items;
    } catch (Exception invalid) {
      throw ApiException.badRequest(
          "Проверьте позиции КП: до 200 строк, количество больше нуля, цена неотрицательная.");
    }
  }

  private static BigDecimal decimal(String value) {
    if (value == null || !value.matches("[0-9]{1,9}(\\.[0-9]{1,4})?"))
      throw new IllegalArgumentException();
    return new BigDecimal(value);
  }

  public static String total(List<Item> items) {
    return items.stream()
        .map(Item::total)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(2, RoundingMode.HALF_UP)
        .toPlainString();
  }
}
