package ru.ticket.journey;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import ru.ticket.dto.*;

class JourneyRouteCreditsTest {
  @Test void sevenCreditsStayCompactAndKeepEveryAttributionAndTheSharedLicense() {
    var routes=mock(JourneyRouteService.class);
    var media=mock(JourneyMediaService.class);
    var asset=new JourneyMedia();asset.width=2200;asset.height=1800;
    asset.contentType="image/jpeg";asset.content=new byte[]{1};
    when(media.get("map")).thenReturn(asset);
    var stops=new ArrayList<JourneyRouteService.Stop>();
    var photos=new ArrayList<JourneyRouteService.Photo>();
    for(int i=0;i<7;i++){
      stops.add(new JourneyRouteService.Stop("s"+i,"Place "+i,30,60,"Europe/Moscow",20));
      photos.add(new JourneyRouteService.Photo("s"+i,"image","https://commons.wikimedia.org/wiki/File:Place_"+i+".jpg","Photographer "+i,"CC BY-SA 4.0","https://creativecommons.org/licenses/by-sa/4.0/"));
    }
    when(routes.get("route")).thenReturn(new JourneyRouteService.Snapshot(stops,List.of(),"2026-11-14T09:00",List.of(),"map",photos));
    var document=new ExportRequest();document.slides=new ArrayList<>();
    new JourneyRouteLayout(routes,media).append(document,new JourneyService.View("test",JourneyService.Kind.TOUR,"Walk",Map.of("route","route"),Instant.EPOCH));
    assertThat(document.slides).hasSize(1);
    var credits=document.slides.get(0).elements.stream().filter(e->e instanceof TextElement t&&t.fontSize==8).map(e->(TextElement)e).toList();
    assertThat(credits).hasSize(8);
    for(int i=0;i<7;i++)assertThat(credits.get(i).text).contains("Photographer "+i,"File:Place_"+i+".jpg");
    assertThat(credits.get(7).text).contains("https://creativecommons.org/licenses/by-sa/4.0/");
    assertThat(credits.get(7).y+credits.get(7).height-credits.get(0).y).isLessThan(180);
    for(int i=1;i<credits.size();i++)assertThat(credits.get(i).y).isGreaterThanOrEqualTo(credits.get(i-1).y+credits.get(i-1).height);
  }
}
