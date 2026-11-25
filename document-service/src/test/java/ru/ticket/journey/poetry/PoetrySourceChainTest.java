package ru.ticket.journey.poetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PoetrySourceChainTest {
  private final PoemShipProvider ship=mock(PoemShipProvider.class);
  private final PoetryDbProvider db=mock(PoetryDbProvider.class);
  private final PoeTreeProvider tree=mock(PoeTreeProvider.class);
  private final WikisourcePoems wiki=mock(WikisourcePoems.class);
  private final PoetryQuery query=new PoetryQuery("en","heartfelt","middle",null,"seed",List.of());
  private List<PoetryWork> works(){return IntStream.range(0,7).mapToObj(i->new PoetryWork("Fixture "+i,"Body "+i,"Author","","","","https://example.test/"+i,"",Map.<String,Object>of("source","fixture"))).toList();}
  @Test void primarySuccessSkipsFallbackAndReusesSnapshotCandidates() throws Exception {
    when(ship.find(any())).thenReturn(works());var chain=new PoetrySourceChain(ship,db,tree,wiki);
    assertEquals(7,chain.select(query).size());assertEquals(7,chain.select(query).size());
    verify(ship,times(1)).find(any());verifyNoInteractions(db,tree,wiki);
  }
  @Test void outageFallsThroughInOrder() throws Exception {
    when(ship.find(any())).thenThrow(new IOException("offline"));when(db.find(any())).thenReturn(List.of());when(tree.find(any())).thenReturn(works());
    assertEquals(7,new PoetrySourceChain(ship,db,tree,wiki).select(query).size());
    var order=inOrder(ship,db,tree);order.verify(ship).find(any());order.verify(db).find(any());order.verify(tree).find(any());verifyNoInteractions(wiki);
  }
  @Test void oldCardJsonRemainsReadableAndNewMetadataRoundTrips() throws Exception {
    var mapper=new ObjectMapper();
    var old=mapper.readValue("{\"language\":\"en\",\"mood\":\"heartfelt\",\"audience\":\"middle\",\"cards\":[{\"date\":\"2026-11-13\",\"title\":\"Fixture\",\"poem\":\"Body\",\"author\":\"Author\"}]}",PoetryCardsService.Bundle.class);
    assertNull(old.cards().get(0).sourceMetadata());
    var card=new PoetryCardsService.Card("date","title","body","author","","","","","",Map.of("source","poemship","poemship_id","id"));
    assertEquals(card,mapper.readValue(mapper.writeValueAsBytes(card),PoetryCardsService.Card.class));
  }
}
