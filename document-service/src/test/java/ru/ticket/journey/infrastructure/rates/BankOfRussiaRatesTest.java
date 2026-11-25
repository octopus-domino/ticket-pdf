package ru.ticket.journey.infrastructure.rates;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BankOfRussiaRatesTest {
  @Test
  void dividesThePublishedPriceByTheCurrencyNominal() throws Exception {
    var file = """
        <ValCurs Date="20.11.2026">
          <Valute><CharCode>JPY</CharCode><Nominal>100</Nominal><Value>45,7590</Value></Valute>
          <Valute><CharCode>USD</CharCode><Nominal>1</Nominal><Value>80,0000</Value></Valute>
        </ValCurs>
        """;
    var snapshot = BankOfRussiaRates.parse(file.getBytes(StandardCharsets.UTF_8));
    assertThat(snapshot.date()).isEqualTo(LocalDate.of(2026, 11, 20));
    assertThat(snapshot.rates().get("JPY")).isEqualByComparingTo("0.45759");
    assertThat(snapshot.rates().get("RUB")).isEqualByComparingTo("1");
  }
}
