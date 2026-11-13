package ru.ticket.journey.walk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import ru.ticket.journey.*;
import ru.ticket.mcp.CommonsMcpClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PhotoEnricherTest {
  private final CommonsMcpClient commons=mock(CommonsMcpClient.class);
  private final WalkRemote remote=mock(WalkRemote.class);
  private final WikimapiaPoiSource wiki=mock(WikimapiaPoiSource.class);
  private final JourneyMediaService media=mock(JourneyMediaService.class);
  private JourneyRouteService.Stop stop(){return new JourneyRouteService.Stop("point","Courtyard",37.62,55.75,"Europe/Moscow",30,"",null,Map.of("wikimapia_id","123","theme","mystic"));}
  @Test void commonsMcpHasPriorityOverWikimapia() throws Exception {
    when(commons.enabled()).thenReturn(true);var image=new CommonsMcpClient.CommonsImage("https://upload.wikimedia.org/fixture.jpg","https://commons.wikimedia.org/wiki/File:Fixture.jpg","Photographer","CC BY 4.0","https://creativecommons.org/licenses/by/4.0/");
    when(commons.searchByName(anyString(),anyDouble(),anyDouble(),anyInt(),anyInt())).thenReturn(List.of(image));
    assertEquals(image,new PhotoEnricher(commons,remote,wiki,media).find(stop(),"en"));verifyNoInteractions(remote,wiki,media);
  }
  @Test void fallbackRequiresAnExplicitReusableWikimapiaLicense() throws Exception {
    when(remote.get(anyString())).thenReturn(new ObjectMapper().createObjectNode());when(wiki.enabled()).thenReturn(true);
    var point=new WikimapiaPoiSource.WikimapiaPoi(123,"Courtyard",37.62,55.75,List.of(),true);
    when(wiki.enrich(123,"en")).thenReturn(new WikimapiaPoiSource.WikimapiaPoiDetails(point,"Description",List.of(),List.of(
        new WikimapiaPoiSource.WikimapiaPhoto("https://photos.wikimapia.org/p/01_big.jpg","https://photos.wikimapia.org/p/01.jpg","Photographer","CC BY-SA 4.0"))));
    var image=new PhotoEnricher(commons,remote,wiki,media).find(stop(),"en");assertNotNull(image);assertEquals("https://creativecommons.org/licenses/by-sa/4.0/",image.licenseUrl());
    when(wiki.enrich(123,"en")).thenReturn(new WikimapiaPoiSource.WikimapiaPoiDetails(point,"Description",List.of(),List.of(
        new WikimapiaPoiSource.WikimapiaPhoto("https://photos.wikimapia.org/p/01_big.jpg","https://photos.wikimapia.org/p/01.jpg","Photographer","unknown"))));
    assertNull(new PhotoEnricher(commons,remote,wiki,media).find(stop(),"en"));verifyNoInteractions(media);
  }
  @Test void commonsLicenseMetadataWithoutCopyrightCanProvePublicDomain() throws Exception {
    var json=new ObjectMapper().readTree("{\"query\":{\"pages\":[{\"imageinfo\":[{\"url\":\"https://upload.wikimedia.org/wikipedia/commons/a/ab/Fixture.jpg\",\"descriptionurl\":\"https://commons.wikimedia.org/wiki/File:Fixture.jpg\",\"extmetadata\":{\"Copyrighted\":{\"value\":\"False\"},\"Artist\":{\"value\":\"<b>Photographer</b>\"}}}]}]}}");
    var images=CommonsImages.parse(json);assertEquals(1,images.size());assertEquals("Photographer",images.get(0).author());assertEquals("https://creativecommons.org/publicdomain/mark/1.0/",images.get(0).licenseUrl());
  }
}
