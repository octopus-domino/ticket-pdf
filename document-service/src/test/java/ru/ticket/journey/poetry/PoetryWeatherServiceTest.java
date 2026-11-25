package ru.ticket.journey.poetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import org.junit.jupiter.api.Test;
import ru.ticket.common.ApiException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PoetryWeatherServiceTest {
  @Test void selectsSevenLocalDatesInsteadOfUtcDates() {
    var now = Instant.parse("2026-11-12T22:00:00Z");
    var forecast = PoetryWeatherService.parse(samples(now), ZoneId.of("Asia/Vladivostok"), now);
    assertEquals("2026-11-13", forecast.days().get(0).date());
    assertEquals("2026-11-19", forecast.days().get(6).date());
  }

  @Test void insufficientCachedForecastReturnsServiceUnavailableConsistently() throws Exception {
    var remote = mock(PoetryRemoteJson.class);
    var payload = new ObjectMapper().createObjectNode();
    payload.putObject("properties").putArray("timeseries");
    when(remote.get(anyString(), anyInt())).thenReturn(payload);
    var service = new PoetryWeatherService(remote, "https://example.test/forecast");
    for (int attempt = 0; attempt < 2; attempt++) {
      var error = assertThrows(ApiException.class, () -> service.forecast(55.75, 37.62, "Europe/Moscow"));
      assertEquals("journey.poetry.weather-unavailable", error.getMessage());
    }
    verify(remote, times(1)).get(anyString(), anyInt());
  }

  private com.fasterxml.jackson.databind.JsonNode samples(Instant now) {
    var root = new ObjectMapper().createObjectNode();
    var series = root.putObject("properties").putArray("timeseries");
    for (int hour = 0; hour < 216; hour += 6) {
      var item = series.addObject().put("time", now.plusSeconds(hour * 3600L).toString());
      var data = item.putObject("data");
      data.putObject("instant").putObject("details").put("air_temperature", 12);
      data.putObject("next_6_hours").putObject("summary").put("symbol_code", "cloudy");
    }
    return root;
  }
}
