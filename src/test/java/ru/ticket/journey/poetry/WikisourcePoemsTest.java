package ru.ticket.journey.poetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WikisourcePoemsTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test void proseEpigraphIsNotPoetryGenre() {
    var page = page("Романы", "Глава I");
    assertFalse(WikisourcePoems.poetryGenre("ru", page));
    assertTrue(WikisourcePoems.poetryGenre("ru", page("Стихотворения 1841 года", "Утёс")));
  }

  @Test void allSupportedLanguagesRecognizeExplicitPoetryCategories() {
    String[] languages = {"ru", "en", "zh", "pl", "it", "es", "nl", "fr", "de"};
    String[] categories = {"Поэзия", "Poems", "唐詩", "Poezja", "Poesie", "Poemas", "Gedichten", "Poèmes", "Gedichte"};
    for (int i = 0; i < languages.length; i++)
      assertTrue(WikisourcePoems.poetryGenre(languages[i], page(categories[i], "Work")), languages[i]);
  }

  @Test void missingLicenseOrDifferentAuthorCannotBeAccepted() {
    var poet = new PoetDiscovery.Poet("Q1", "Author", "Author:Writer", "Q2", 1);
    var page = page("Poems", "Work");
    page.put("text", "<span class='ws-author'><a href='/wiki/Author:Other'>Other</a></span>"
        + "<div class='poem'>A sufficiently long first line of a poem<br>Another complete line of the poem<br>Third complete line of this poem<br>The final complete line of this poem</div>");
    assertTrue(WikisourcePoems.parse("en", poet, page, true).isEmpty());
    page.put("text", page.path("text").asText().replace("Author:Other", "Author:Writer"));
    assertTrue(WikisourcePoems.parse("en", poet, page, false).isEmpty());
    assertTrue(WikisourcePoems.parse("en", poet, page, true).isPresent());
  }

  @Test void collectionCannotUseAnEpigraphToCompleteSevenCards() throws Exception {
    var remote = mock(PoetryRemoteJson.class); var discovery = mock(PoetDiscovery.class);
    when(discovery.discover("en")).thenReturn(java.util.List.of(new PoetDiscovery.Poet("Q1", "Writer", "Author:Writer", "Q2", 1)));
    var search = mapper.createObjectNode();
    var results = search.putObject("query").putArray("search");
    for (int index = 0; index < 7; index++) results.addObject().put("title", "Fixture " + index).put("wordcount", 120);
    when(remote.get(anyString(), anyInt())).thenAnswer(call -> {
      String url = call.getArgument(0);
      if (url.contains("list=search")) return search;
      int index = Integer.parseInt(java.net.URLDecoder.decode(url.substring(url.lastIndexOf("page=") + 5), java.nio.charset.StandardCharsets.UTF_8).replace("Fixture ", ""));
      var work = page(index == 0 ? "Novels" : "Poems", "Fixture " + index);
      work.withArray("categories").addObject().put("category", "PD-old-70");
      work.put("text", "<span class='ws-author'><a href='/wiki/Author:Writer'>Writer</a></span><div class='poem'>"
          + index + " synthetic example first line<br>Another complete fixture line<br>A third complete fixture line<br>The final complete fixture line</div>");
      return mapper.createObjectNode().set("parse", work);
    });
    var service = new WikisourcePoems(remote, discovery);
    var error = assertThrows(ru.ticket.common.ApiException.class,
        () -> service.poems("en", "heartfelt", "middle"));
    assertEquals("journey.poetry.not-enough", error.getMessage());
    assertTrue(error.getCause().getMessage().endsWith("6"));
    assertThrows(ru.ticket.common.ApiException.class, () -> service.poems("en", "heartfelt", "middle"));
    verify(discovery, times(1)).discover("en");
  }

  @Test void unrelatedAuthorSearchCannotHideWorksFromTheirActualAuthor() throws Exception {
    var remote = mock(PoetryRemoteJson.class); var discovery = mock(PoetDiscovery.class);
    when(discovery.discover("en")).thenReturn(java.util.List.of(
        new PoetDiscovery.Poet("Q1", "Other", "Author:Other", "Q2", 1),
        new PoetDiscovery.Poet("Q3", "Writer", "Author:Writer", "Q2", 1)));
    var search = mapper.createObjectNode(); var results = search.putObject("query").putArray("search");
    for (int i = 0; i < 7; i++) results.addObject().put("title", "Fixture " + i).put("wordcount", 120);
    when(remote.get(anyString(), anyInt())).thenAnswer(call -> {
      String url = call.getArgument(0);
      if (url.contains("list=search")) return search;
      String title = java.net.URLDecoder.decode(url.substring(url.lastIndexOf("page=") + 5), java.nio.charset.StandardCharsets.UTF_8);
      var work = page("Poems", title); work.withArray("categories").addObject().put("category", "PD-old-70");
      work.put("text", "<span class='ws-author'><a href='/wiki/Author:Writer'>Writer</a></span><div class='poem'>"
          + title + " synthetic example first line<br>Another complete fixture line<br>A third complete fixture line<br>The final complete fixture line</div>");
      return mapper.createObjectNode().set("parse", work);
    });
    var poems = new WikisourcePoems(remote, discovery).poems("en", "heartfelt", "middle");
    assertEquals(7, poems.size());
    assertTrue(poems.stream().allMatch(poem -> poem.authorId().equals("Q3")));
  }

  @Test void htmlFormattingWhitespaceDoesNotDoubleVerseLines() {
    var work = page("Poems", "Layout fixture");
    var html = new StringBuilder("<span class='ws-author'><a href='/wiki/Author:Writer'>Writer</a></span><div class='poem'><p>");
    for (int i = 0; i < 24; i++) html.append("Synthetic verse line ").append(i).append("<br>\n");
    work.put("text", html.append("</p></div>").toString());
    var parsed = WikisourcePoems.parse("en", new PoetDiscovery.Poet("Q1", "Writer", "Author:Writer", "Q2", 1), work, true);
    assertTrue(parsed.isPresent());
    assertEquals(24, parsed.get().text().lines().filter(line -> !line.isBlank()).count());
  }

  private ObjectNode page(String category, String title) {
    var page = mapper.createObjectNode().put("title", title);
    page.putArray("categories").addObject().put("category", category);
    return page;
  }
}
