package ru.ticket.journey;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import ru.ticket.common.ApiException;

final class JourneyMoney {
  static BigDecimal amount(String value) {
    try {
      var result = new BigDecimal(value.replaceAll("[\\s\u00a0\u202f]", "").replace(',', '.'));
      if (result.signum() < 0 || result.precision() > 16 || result.scale() > 4)
        throw new NumberFormatException();
      return result;
    } catch (IllegalArgumentException error) {
      throw ApiException.badRequest("journey.price.invalid");
    }
  }

  static String formatted(Map<String, String> data) {
    try {
      var format =
          NumberFormat.getCurrencyInstance(
              Locale.forLanguageTag(data.getOrDefault("locale", "ru")));
      format.setCurrency(Currency.getInstance(data.getOrDefault("currency", "RUB")));
      return format.format(amount(data.getOrDefault("price", "0")));
    } catch (IllegalArgumentException error) {
      throw ApiException.badRequest("journey.price.invalid");
    }
  }
}
