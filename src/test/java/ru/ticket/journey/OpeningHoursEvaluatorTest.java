package ru.ticket.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class OpeningHoursEvaluatorTest {
  @ParameterizedTest
  @CsvSource({"Mo-Fr 09:00-18:00,2026-11-13T10:00,OPEN","Mo-Fr 09:00-18:00,2026-11-13T20:00,CLOSED",
      "Mo-Fr 09:00-18:00,2026-11-14T10:00,UNKNOWN","24/7,2026-11-14T23:59,OPEN","off,2026-11-13T10:00,CLOSED",
      "sunrise-sunset,2026-11-13T10:00,UNKNOWN","Mo-Su 22:00-04:00,2026-11-13T23:00,UNKNOWN"})
  void evaluatesConservatively(String hours,String visit,String status){
    assertEquals(status,new OpeningHoursEvaluator(new ObjectMapper()).evaluate(hours,LocalDateTime.parse(visit)).name());
  }
}
