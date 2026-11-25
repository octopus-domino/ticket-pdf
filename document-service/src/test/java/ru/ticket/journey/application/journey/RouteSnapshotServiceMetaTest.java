package ru.ticket.journey.application.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouteSnapshotServiceMetaTest {
  private RouteSnapshotService.Snapshot snapshot(Map<String,String> meta){
    return new RouteSnapshotService.Snapshot(List.of(new RouteSnapshotService.Stop("a","First",37.62,55.75,"Europe/Moscow",0,"",null,meta),
        new RouteSnapshotService.Stop("b","Second",37.621,55.751,"Europe/Moscow",0)),List.of(new RouteSnapshotService.Leg("foot",60,null,null,null,null,List.of(List.of(37.62,55.75),List.of(37.621,55.751)),100.0)),
        "2026-11-13T20:00",List.of(new RouteSnapshotService.Row("stop","First","2026-11-13T17:00:00Z","2026-11-13T17:00:00Z","Europe/Moscow")),"a".repeat(64));
  }
  @Test void metadataIsOptionalAndOldSnapshotJsonStillWorks() throws Exception {
    var mapper=new ObjectMapper();var current=snapshot(Map.of("source","wikimapia","theme","mystic","visit_status","UNKNOWN","in_osm","false"));
    RouteSnapshotService.validate(current);assertEquals(current,mapper.readValue(mapper.writeValueAsBytes(current),RouteSnapshotService.Snapshot.class));
    var old=mapper.valueToTree(snapshot(null));for(var stop:old.path("stops"))((com.fasterxml.jackson.databind.node.ObjectNode)stop).remove("meta");
    var restored=mapper.treeToValue(old,RouteSnapshotService.Snapshot.class);RouteSnapshotService.validate(restored);assertNull(restored.stops().get(0).meta());
    ((com.fasterxml.jackson.databind.node.ObjectNode)old).put("pointNumbering","interior");
    var numbered=mapper.treeToValue(old,RouteSnapshotService.Snapshot.class);RouteSnapshotService.validate(numbered);assertEquals("interior",numbered.pointNumbering());
  }
  @Test void metadataCannotBypassSizeAndKeyLimits(){
    assertThrows(ru.ticket.common.ApiException.class,()->RouteSnapshotService.validate(snapshot(Map.of("invalid-key","value"))));
    assertThrows(ru.ticket.common.ApiException.class,()->RouteSnapshotService.validate(snapshot(Map.of("description","x".repeat(501)))));
    var many=new HashMap<String,String>();for(char c='a';c<='k';c++)many.put(String.valueOf(c),"value");
    assertThrows(ru.ticket.common.ApiException.class,()->RouteSnapshotService.validate(snapshot(many)));
  }

  @Test void largerPrivateWalksDoNotRaiseTheMainTripLimit() {
    var base = snapshot(null);
    var stops = java.util.stream.IntStream.range(0, 13)
        .mapToObj(index -> new RouteSnapshotService.Stop("point-" + index, "Point " + index,
            37.62, 55.75, "Europe/Moscow", 0)).toList();
    var legs = java.util.stream.IntStream.range(0, 12).mapToObj(index -> base.legs().get(0)).toList();
    var walk = new RouteSnapshotService.Snapshot(stops, legs, base.startLocal(), base.timeline(), base.mapId(), null, "interior");
    assertDoesNotThrow(() -> RouteSnapshotService.validate(walk));
    var trip = new RouteSnapshotService.Snapshot(stops, legs, base.startLocal(), base.timeline(), base.mapId());
    assertThrows(ru.ticket.common.ApiException.class, () -> RouteSnapshotService.validate(trip));
    var car = new RouteSnapshotService.Leg("car", 60, null, null, null, null, base.legs().get(0).coordinates(), 100.0);
    var carLegs = new ArrayList<>(legs); carLegs.set(0, car);
    var invalidWalk = new RouteSnapshotService.Snapshot(stops, carLegs, base.startLocal(), base.timeline(), base.mapId(), null, "interior");
    assertThrows(ru.ticket.common.ApiException.class, () -> RouteSnapshotService.validate(invalidWalk));
  }
}
