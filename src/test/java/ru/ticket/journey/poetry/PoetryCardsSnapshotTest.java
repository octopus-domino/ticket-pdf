package ru.ticket.journey.poetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PoetryCardsSnapshotTest {
  @Test void freezesSourceMetadataWithoutChangingWeatherOrOldInputContract() throws Exception {
    var weather=mock(PoetryWeatherService.class);var wiki=mock(WikisourcePoems.class);
    var sources=mock(PoetrySourceChain.class);var repository=mock(PoetrySetRepository.class);var mapper=new ObjectMapper();
    var days=IntStream.range(0,7).mapToObj(i->new PoetryWeatherService.Day("2026-11-"+(13+i),"cloudy",10)).toList();
    when(weather.forecast(55.75,37.62,"Europe/Moscow")).thenReturn(new PoetryWeatherService.Forecast("Europe/Moscow",days,Instant.now(),"https://example.test/weather","https://example.test/license"));
    var works=IntStream.range(0,7).mapToObj(i->new PoetryWork("Fixture "+i,"Synthetic body "+i,"Author","","","","https://example.test/"+i,"",Map.<String,Object>of("source","poemship","mood","joyful","language","ru","poemship_id","id"+i))).toList();
    when(sources.select(any())).thenReturn(works);
    var service=new PoetryCardsService(weather,wiki,repository,mapper);service.sources(sources);
    var input=new PoetryCardsService.Input(55.75,37.62,"Europe/Moscow","ru","funny","child","seed","радостный");
    var prepared=service.prepare(input);assertEquals(7,prepared.bundle().cards().size());
    var stored=ArgumentCaptor.forClass(PoetrySet.class);verify(repository).saveAndFlush(stored.capture());
    var frozen=mapper.readValue(stored.getValue().payload,PoetryCardsService.Bundle.class);
    assertEquals(prepared.bundle(),frozen);assertEquals(prepared.id(),stored.getValue().id);
    assertEquals("радостный",frozen.cards().get(0).sourceMetadata().get("requested_mood"));
    assertEquals("smile",frozen.cards().get(0).sourceMetadata().get("need"));
    when(repository.findById(prepared.id())).thenReturn(Optional.of(stored.getValue()));
    assertEquals(frozen,service.get(prepared.id()));verifyNoInteractions(wiki);
    verify(weather,times(1)).forecast(55.75,37.62,"Europe/Moscow");
    assertNull(new PoetryCardsService.Input(55.75,37.62,"Europe/Moscow","ru","funny","child","seed").moodText());
  }
}
