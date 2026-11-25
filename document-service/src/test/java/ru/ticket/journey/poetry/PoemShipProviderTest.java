package ru.ticket.journey.poetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PoemShipProviderTest {
  private final ObjectMapper mapper=new ObjectMapper();
  private final PoemShipMcpClient client=mock(PoemShipMcpClient.class);
  private final PoetryDbProvider db=mock(PoetryDbProvider.class);
  private final PoeTreeProvider tree=mock(PoeTreeProvider.class);
  private final WikisourcePoems wiki=mock(WikisourcePoems.class);
  private final PoemShipTextResolver texts=mock(PoemShipTextResolver.class);
  private PoetryQuery query(){return new PoetryQuery("ru","funny","child","joyful","seed",List.of());}
  private void guide() throws Exception {
    when(client.call(eq("library_guide"),anyMap())).thenReturn(mapper.readTree("{\"moods\":[{\"name\":\"joyful\"}]}"));
  }
  private String candidate(boolean complete){return "{\"poems\":[{\"id\":\"5a61b74f03033e3e9f5620ce\",\"title\":\"Fixture\",\"author\":\"Fixture author\",\"language\":\"ru\",\"firstLines\":[\"Synthetic fixture\"],\"textIsComplete\":"+complete+",\"url\":\"https://poets.by/poems/5a61b74f03033e3e9f5620ce\"}]}";}
  @Test void passesAgeMoodAndLengthToProviderAndCachesProvenance() throws Exception {
    guide();when(client.call(eq("find_poems"),anyMap())).thenReturn(mapper.readTree(candidate(true)));
    var provider=new PoemShipProvider(client,db,tree,wiki,texts);
    var works=provider.find(query());assertEquals(1,works.size());
    assertEquals("poemship",works.get(0).metadata().get("source"));
    assertEquals("5a61b74f03033e3e9f5620ce",works.get(0).metadata().get("poemship_id"));
    assertEquals(works,provider.find(query()));
    verify(client).call(eq("find_poems"),argThat(args->args.get("audience").equals("children")&&args.get("mood").equals("joyful")&&args.get("need").equals("smile")&&args.get("max_lines").equals(20)&&args.get("easy_to_read").equals(true)));
    verifyNoInteractions(db,tree,wiki);
  }
  @Test void excerptWithoutFullTextIsNeverPublished() throws Exception {
    guide();when(client.call(eq("find_poems"),anyMap())).thenReturn(mapper.readTree(candidate(false)));
    when(db.resolve(anyString(),anyString(),any())).thenReturn(Optional.empty());
    when(tree.resolve(anyString(),anyString(),any())).thenReturn(Optional.empty());
    when(wiki.resolve(anyString(),anyString(),anyString())).thenReturn(Optional.empty());
    assertTrue(new PoemShipProvider(client,db,tree,wiki,texts).find(query()).isEmpty());
  }
  @Test void resolvesExactFullTextAfterFirstResolverFailure() throws Exception {
    guide();when(client.call(eq("find_poems"),anyMap())).thenReturn(mapper.readTree(candidate(false)));
    when(db.resolve(anyString(),anyString(),any())).thenThrow(new IOException("offline"));
    when(tree.resolve(anyString(),anyString(),any())).thenReturn(Optional.of(new PoetryWork("Fixture","Synthetic fixture\nFull body", "Fixture author","","","","https://example.test/work","",Map.of("source","poetree"))));
    var result=new PoemShipProvider(client,db,tree,wiki,texts).find(query());
    assertEquals(1,result.size());assertEquals("poetree",result.get(0).metadata().get("text_source"));
    verifyNoInteractions(wiki);
  }
  @Test void unsupportedLanguageDoesNotCallMcp() throws Exception {
    assertTrue(new PoemShipProvider(client,db,tree,wiki,texts).find(new PoetryQuery("zh","heartfelt","middle",null,"s",List.of())).isEmpty());
    verifyNoInteractions(client);
  }
  @Test void emptyGuideSkipsSearch() throws Exception {
    when(client.call(eq("library_guide"),anyMap())).thenReturn(mapper.readTree("{\"moods\":[]}"));
    assertTrue(new PoemShipProvider(client,db,tree,wiki,texts).find(query()).isEmpty());
    verify(client,never()).call(eq("find_poems"),anyMap());
  }
}
