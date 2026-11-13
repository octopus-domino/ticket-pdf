package ru.ticket.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JourneyRouteServiceMetaTest {
  private JourneyRouteService.Snapshot snapshot(Map<String,String> meta){
    return new JourneyRouteService.Snapshot(List.of(new JourneyRouteService.Stop("a","First",37.62,55.75,"Europe/Moscow",0,"",null,meta),
        new JourneyRouteService.Stop("b","Second",37.621,55.751,"Europe/Moscow",0)),List.of(new JourneyRouteService.Leg("foot",60,null,null,null,null,List.of(List.of(37.62,55.75),List.of(37.621,55.751)),100.0)),
        "2026-11-13T20:00",List.of(new JourneyRouteService.Row("stop","First","2026-11-13T17:00:00Z","2026-11-13T17:00:00Z","Europe/Moscow")),"a".repeat(64));
  }
  @Test void metadataIsOptionalAndOldSnapshotJsonStillWorks() throws Exception {
    var mapper=new ObjectMapper();var current=snapshot(Map.of("source","wikimapia","theme","mystic","visit_status","UNKNOWN","in_osm","false"));
    JourneyRouteService.validate(current);assertEquals(current,mapper.readValue(mapper.writeValueAsBytes(current),JourneyRouteService.Snapshot.class));
    var old=mapper.valueToTree(snapshot(null));for(var stop:old.path("stops"))((com.fasterxml.jackson.databind.node.ObjectNode)stop).remove("meta");
    var restored=mapper.treeToValue(old,JourneyRouteService.Snapshot.class);JourneyRouteService.validate(restored);assertNull(restored.stops().get(0).meta());
  }
  @Test void metadataCannotBypassSizeAndKeyLimits(){
    assertThrows(ru.ticket.common.ApiException.class,()->JourneyRouteService.validate(snapshot(Map.of("invalid-key","value"))));
    assertThrows(ru.ticket.common.ApiException.class,()->JourneyRouteService.validate(snapshot(Map.of("description","x".repeat(501)))));
    var many=new HashMap<String,String>();for(char c='a';c<='k';c++)many.put(String.valueOf(c),"value");
    assertThrows(ru.ticket.common.ApiException.class,()->JourneyRouteService.validate(snapshot(many)));
  }
}
