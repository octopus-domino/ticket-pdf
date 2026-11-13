package ru.ticket.journey;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.ticket.common.ApiException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FallbackRoadRouterTest {
  @Test void upstreamNoTrackDoesNotPreventOtherProvidersFromFindingTheRoad() {
    var primary=mock(BRouterHttpRoadRouter.class);
    var next=mock(ValhallaRoadRouter.class);
    var start=new RoadRouter.Point(37.62,55.75);
    var end=new RoadRouter.Point(37.621,55.751);
    var road=new RoadRouter.Road(List.of(List.of(37.62,55.75),List.of(37.621,55.751)),100,60);
    when(primary.route(start,end,"foot")).thenThrow(new ApiException(HttpStatus.BAD_REQUEST,"journey.route.invalid-route"));
    when(next.route(start,end,"foot")).thenReturn(road);
    assertSame(road,new FallbackRoadRouter(primary,next,null,null).route(start,end,"foot"));
  }

  @Test void malformedInputNeverReachesRemoteProviders() {
    var primary=mock(BRouterHttpRoadRouter.class);
    var router=new FallbackRoadRouter(primary,null,null,null);
    assertThrows(ApiException.class,()->router.route(new RoadRouter.Point(Double.NaN,55),new RoadRouter.Point(37,55),"foot"));
    verifyNoInteractions(primary);
  }
}
