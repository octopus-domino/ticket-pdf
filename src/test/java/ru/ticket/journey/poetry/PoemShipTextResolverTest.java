package ru.ticket.journey.poetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoemShipTextResolverTest {
  private final ObjectMapper mapper=new ObjectMapper();
  private final PoemShipTextResolver resolver=new PoemShipTextResolver(mapper);
  private final PoetryQuery query=new PoetryQuery("en","heartfelt","middle",null,"s",List.of());
  private String html(String author,int lines) throws Exception {
    var work=mapper.createObjectNode().put("@type","CreativeWork").put("name","Fixture").put("inLanguage","en").put("text",lines==2?"Synthetic first line\nSynthetic second line":"Synthetic first line");
    work.putObject("author").put("name",author);
    return "<script type='application/ld+json'>"+work+"</script><article><div class='poem-text'><span class='PoemText__line'>Synthetic first line</span>"
        +(lines==2?"<span class='PoemText__line'>Synthetic second line</span>":"")+"</div></article><div class='preview'>Unrelated suggestion</div>";
  }
  @Test void readsOnlyMatchingCompleteStructuredWork() throws Exception {
    var candidate=mapper.readTree("{\"title\":\"Fixture\",\"author\":\"Author\",\"lines\":2}");
    var work=resolver.parse(html("Author",2),candidate,query,"https://poemship.com/poems/id").orElseThrow();
    assertEquals("Synthetic first line\nSynthetic second line",work.text());
    assertEquals("poemship",work.metadata().get("source"));
  }
  @Test void rejectsWrongAuthorAndIncompleteStructuredExcerpt() throws Exception {
    var candidate=mapper.readTree("{\"title\":\"Fixture\",\"author\":\"Author\",\"lines\":2}");
    assertTrue(resolver.parse(html("Other author",2),candidate,query,"https://example.test").isEmpty());
    assertTrue(resolver.parse(html("Author",1),candidate,query,"https://example.test").isEmpty());
  }
  @Test void structuredPreviewCannotTruncateTheVisiblePoem() throws Exception {
    var candidate=mapper.readTree("{\"title\":\"Fixture\",\"author\":\"Author\",\"lines\":2}");
    String page=html("Author",2).replace("Synthetic first line\\nSynthetic second line","Synthetic first line");
    assertEquals("Synthetic first line\nSynthetic second line",resolver.parse(page,candidate,query,"https://example.test").orElseThrow().text());
  }
  @Test void neverFetchesExternalOrCredentialedCandidateUrls() throws Exception {
    for(String url:List.of("https:/poems/5a61b74f03033e3e9f5620ce","https://example.test/poems/5a61b74f03033e3e9f5620ce","https://user:secret@poemship.com/poems/5a61b74f03033e3e9f5620ce","https://poemship.com:8443/poems/5a61b74f03033e3e9f5620ce")){
      var candidate=mapper.createObjectNode().put("id","5a61b74f03033e3e9f5620ce").put("url",url);
      assertTrue(resolver.resolve(candidate,query).isEmpty());
    }
  }
}
