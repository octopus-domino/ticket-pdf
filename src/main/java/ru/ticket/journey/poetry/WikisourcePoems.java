package ru.ticket.journey.poetry;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;

/** Reads complete short poems and explicit source/author/licence evidence at request time. */
@Service
public class WikisourcePoems {
  public record Poem(String title, String text, String author, String authorId, String authorUrl,
      String country, int popularity, String sourceUrl, String licenseUrl, String categories) {}
  private record Cached(long until, List<Poem> poems) {}
  private record Candidate(String title, boolean inheritedLicense, boolean inheritedGenre, String work) {}
  private final Map<String,Cached> cache = new LinkedHashMap<>(32, .75f, true);
  private final PoetryRemoteJson remote;
  private final PoetDiscovery authors;
  private final Semaphore discovery = new Semaphore(1);
  public WikisourcePoems(PoetryRemoteJson remote, PoetDiscovery authors) { this.remote = remote; this.authors = authors; }
  /** Resolve a curated provider hit to a complete licensed work, without reclassifying its mood/age. */
  public Optional<PoetryWork> resolve(String title,String author,String language) throws java.io.IOException {
    if(!Set.of("ru","en","es","de","fr","it","pl","nl","zh").contains(language)||title.length()>300||author.length()>300)return Optional.empty();
    String query="\""+title.replace('"',' ')+"\" \""+author.replaceAll("[,()]+"," ").strip().replaceFirst("^.*\\s","")+"\"";
    var search=api(language,"action=query&list=search&srlimit=4&srnamespace=0&srsearch="+PoetDiscovery.encode(query));
    for(var match:search.path("query").path("search")){
      String pageTitle=match.path("title").asText("");if(pageTitle.isBlank()||pageTitle.endsWith("/ДО")||pageTitle.contains("(ДО)"))continue;
      var page=api(language,"action=parse&prop=text%7Ccategories%7Clinks&page="+PoetDiscovery.encode(pageTitle)).path("parse");
      if(!publicDomain(page)||!poetryGenre(language,page))continue;
      for(var link:page.path("links"))if(link.path("ns").asInt(-1)==102){
        String sourceAuthor=link.path("title").asText("");
        String normalized=PoetryDbProvider.normalize(sourceAuthor);
        boolean same=Arrays.stream(author.split("[\\s.,]+" )).filter(word->word.length()>1).allMatch(word->normalized.contains(PoetryDbProvider.normalize(word)));
        if(!same)continue;
        var parsed=parse(language,new PoetDiscovery.Poet("",author,sourceAuthor,"",0),page,true);
        if(parsed.isPresent())return Optional.of(PoetryWork.wiki(parsed.get(),language,"provider-curated"));
      }
    }return Optional.empty();
  }
  public List<Poem> poems(String language, String mood, String audience) {
    PoetrySelection.validate(language, mood, audience);
    String hint = PoetrySelection.hint(language, mood, audience), key = language + ":" + mood + ":" + audience;
    synchronized (cache) {
      var found = cache.get(key);
      if (found != null && found.until > System.currentTimeMillis()) {
        if (found.poems.size() < 7) throw insufficient(found.poems.size());
        return found.poems;
      }
    }
    if (!discovery.tryAcquire()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.poetry.busy");
    try {
      var result = collect(language, hint, mood, audience);
      if (result.size() < 7) {
        // A retry must not immediately repeat dozens of requests to a public source.
        remember(key, result, Duration.ofMinutes(2));
        throw insufficient(result.size());
      }
      remember(key, result, Duration.ofHours(6));
      return result;
    } catch (java.io.IOException ex) {
      var failure = new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.poetry.sources-unavailable"); failure.initCause(ex); throw failure;
    }
    finally { discovery.release(); }
  }
  private void remember(String key, List<Poem> poems, Duration ttl) {
    synchronized (cache) {
      cache.put(key, new Cached(System.currentTimeMillis() + ttl.toMillis(), poems));
      while (cache.size() > 32) cache.remove(cache.keySet().iterator().next());
    }
  }
  private static ApiException insufficient(int count) {
    var unavailable = new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.poetry.not-enough");
    unavailable.initCause(new IllegalStateException("Verified short poems found: " + count));
    return unavailable;
  }
  private List<Poem> collect(String language, String hint, String mood, String audience) throws java.io.IOException {
    var poets = authors.discover(language); var result = new ArrayList<Poem>(); var visited = new HashSet<String>(); var texts = new HashSet<String>();
    long deadline = System.nanoTime() + Duration.ofSeconds(75).toNanos(); int requests = 0;
    for (var poet : poets) {
      if (System.nanoTime() > deadline || requests >= 64 || result.size() >= 32) break;
      String surname = poet.name().replaceAll("[,()]+", " ").strip().replaceFirst("^.*\\s", "");
      // Author-qualified work titles rank standalone poems ahead of prose mentioning the author.
      String licenseFilter = language.equals("ru") ? " incategory:PD-old-70" : "";
      boolean childCategory = audience.equals("child") && language.equals("ru");
      String themeQuery = childCategory ? " deepcat:\"Детская литература\"" : hint.isBlank() ? "" : " (" + String.join(" OR ", Arrays.stream(hint.split(" ")).map(word -> word + "*").toList()) + ")";
      String query = "\"" + surname + "\" insource:poem" + licenseFilter + themeQuery;
      var search = api(language, "action=query&list=search&srlimit=40&srnamespace=0&srsearch=" + PoetDiscovery.encode(query)); requests++;
      if (!audience.equals("child") && !hint.isBlank() && search.path("query").path("search").isEmpty()) {
        search = api(language, "action=query&list=search&srlimit=40&srnamespace=0&srsearch=" + PoetDiscovery.encode("\"" + surname + "\" insource:poem" + licenseFilter)); requests++;
      }
      var candidates = new ArrayDeque<Candidate>();
      var matches = new ArrayList<JsonNode>(); search.path("query").path("search").forEach(matches::add);
      // Short complete works are suitable for a postcard; do not spend the request budget on novels.
      matches.sort(Comparator.comparingInt(item -> Math.abs(item.path("wordcount").asInt(100000) - 120)));
      for (var item : matches) {
        String title = item.path("title").asText("");
        if (!title.contains("(ДО)") && !title.endsWith("/ДО")) candidates.add(new Candidate(title, false, false, ""));
      }
      int inspected = 0, acceptedBefore = result.size();
      while (!candidates.isEmpty() && inspected++ < 7 && requests < 64 && System.nanoTime() < deadline && result.size() < 32) {
        if (inspected > 4 && result.size() == acceptedBefore) break;
        var candidate = candidates.removeFirst();
        // Search can mention another poet. A failed attribution for one author must not
        // prevent checking the same work against its actual author later in the scan.
        String title = candidate.title(); if (title.isBlank() || title.length() > 500 || !visited.add(poet.id() + ":" + title)) continue;
        try {
          var page = api(language, "action=parse&prop=text%7Ccategories%7Clinks&page=" + PoetDiscovery.encode(title)).path("parse"); requests++;
          boolean licensed = publicDomain(page);
          boolean poetry = poetryGenre(language, page) || candidate.inheritedGenre();
          var parsed = poetry ? parse(language, poet, page, licensed || candidate.inheritedLicense()) : Optional.<Poem>empty();
          if (parsed.isPresent()) {
            if(childCategory) {
              var original=parsed.get();
              parsed=Optional.of(new Poem(original.title(),original.text(),original.author(),original.authorId(),original.authorUrl(),
                  original.country(),original.popularity(),original.sourceUrl(),original.licenseUrl(),original.categories()+" Детская литература (category ancestry)"));
            }
            if (!PoetrySelection.suitable(parsed.get(), language, mood, audience)) continue;
            String fingerprint = parsed.get().text().replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
            String work = poet.id() + ":" + (candidate.work().isBlank() ? title.split("[(/]", 2)[0].strip() : candidate.work());
            if (!texts.contains(work) && texts.add(fingerprint.substring(0, Math.min(120, fingerprint.length())))) {
              texts.add(work); result.add(parsed.get());
            }
            continue;
          }
          // Many source works are edition indexes. Only follow editions of that same work.
          var editions = new ArrayList<String>();
          String root = title.split("\\(", 2)[0].strip().toLowerCase(Locale.ROOT);
          if (poetry && root.length() > 4 && authorMatches(poet, page)) for (var link : page.path("links")) {
            String next = link.path("title").asText("");
            if (link.path("ns").asInt(-1) == 0 && !next.equals(title) && next.length() <= 500
                && next.toLowerCase(Locale.ROOT).contains(root) && !next.contains("(ДО)") && !next.endsWith("/ДО")) editions.add(next);
          }
          if (!editions.isEmpty()) {
            for (int index = Math.min(3, editions.size()) - 1; index >= 0; index--)
              candidates.addFirst(new Candidate(editions.get(index), licensed || candidate.inheritedLicense(), true,
                  candidate.work().isBlank() ? title.split("[(/]", 2)[0].strip() : candidate.work()));
          }
        } catch (java.io.IOException ex) { /* One unavailable work does not stand in for a poem. */ }
      }
    }
    return List.copyOf(result);
  }
  /** A verse-shaped block in prose can be an epigraph by a different author. Require work-level genre evidence. */
  static boolean poetryGenre(String language, JsonNode page) {
    String expression = switch (language) {
      case "ru" -> "поэз|стихотвор|басн|эпиграмм|лирик";
      case "en" -> "poem|poetry|sonnet|fable|nursery|ballad";
      case "zh" -> "詩|诗|詞|词|賦|赋|樂府|乐府|韻文|韵文";
      case "pl" -> "wiersz|poezj|bajk|sonet|pieśn";
      case "it" -> "poesi|sonett|liric|favol|canti";
      case "es" -> "poes|poema|verso|soneto|fábula";
      case "nl" -> "gedicht|poëz|fabel|lied";
      case "fr" -> "poème|poésie|poèmes|poésies|sonnet|fable";
      case "de" -> "gedicht|lyrik|poesie|fabel|ballad|lied";
      default -> "(?!)";
    };
    var pattern = java.util.regex.Pattern.compile(expression, java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
    for (var category : page.path("categories"))
      if (pattern.matcher(category.path("category").asText("")).find()) return true;
    return false;
  }
  private JsonNode api(String language, String query) throws java.io.IOException {
    return remote.get("https://" + language + ".wikisource.org/w/api.php?format=json&formatversion=2&" + query, 1_500_000);
  }
  static boolean publicDomain(JsonNode page) {
    for (var category : page.path("categories")) {
      String value = category.path("category").asText("").toLowerCase(Locale.ROOT);
      if (value.startsWith("pd-old") || value.startsWith("pd/old") || value.contains("public_domain") || value.contains("pd-1923") || value.contains("pd-us-expired")) return true;
    }
    String evidence = Jsoup.parse(page.path("text").asText("")).select(".licensetemplate,.licensetpl,.license,.licence,#license").text().toLowerCase(Locale.ROOT);
    return List.of("public domain", "общественном достоянии", "公有领域", "公有領域", "domena publiczna", "pubblico dominio", "dominio público", "publieke domein", "domaine public", "gemeinfrei")
        .stream().anyMatch(evidence::contains);
  }
  static boolean authorMatches(PoetDiscovery.Poet poet, JsonNode page) {
    var source = Jsoup.parse(page.path("text").asText(""));
    var links = source.select(".ws-author a[href],a.ws-author[href],#header_author_text a[href],#header_author a[href]");
    if (!links.isEmpty()) return links.stream().allMatch(link -> matchesPage(link.attr("href"), poet.sourcePage()));
    var authors = new ArrayList<String>();
    for (var link : page.path("links")) if (link.path("ns").asInt() == 102) authors.add(link.path("title").asText(""));
    return authors.size() == 1 && normalize(authors.get(0)).equals(normalize(poet.sourcePage()));
  }
  static Optional<Poem> parse(String language, PoetDiscovery.Poet poet, JsonNode page, boolean licensed) {
    if (!licensed || !authorMatches(poet, page)) return Optional.empty();
    var html = Jsoup.parse(page.path("text").asText(""));
    var blocks = html.select(".poem").stream().filter(element -> element.parents().stream().noneMatch(parent -> parent.hasClass("poem"))).toList();
    if (blocks.size() != 1) return Optional.empty();
    var body = blocks.get(0).clone(); body.select("sup,.reference,.mw-editsection,.pagenum,.ws-noexport,.linenum,.linenumber,.line-number,.poem-line-number").remove();
    // Formatting newlines in HTML collapse in the browser; counting them alongside <br>
    // doubled verse lines and incorrectly rejected complete 18–32-line poems.
    org.jsoup.select.NodeTraversor.traverse(new org.jsoup.select.NodeVisitor() {
      @Override public void head(Node node, int depth) {
        if (node instanceof TextNode textNode
            && textNode.parent() != null && textNode.parent().closest("pre") == null)
          textNode.text(textNode.getWholeText().replaceAll("[\\r\\n]+[\\t ]*", " "));
      }
      @Override public void tail(Node node, int depth) {}
    }, body);
    for (var br : body.select("br")) br.before(new TextNode("\n")); body.select("br").remove();
    for (var paragraph : body.select("p")) paragraph.appendChild(new TextNode("\n\n"));
    String text = body.wholeText().replace('\u00a0', ' ').replaceAll("[\\t ]+\\n", "\n").replaceAll("\\n{4,}", "\n\n").strip();
    long lines = text.lines().filter(line -> !line.isBlank()).count();
    String title = page.path("title").asText("");
    if (text.length() < (language.equals("zh") ? 12 : 60) || text.length() > 1600 || lines < 4 || lines > 32
        || text.lines().count() > 34 || title.isBlank() || title.length() > 500) return Optional.empty();
    StringBuilder categories = new StringBuilder(); page.path("categories").forEach(category -> categories.append(category.path("category").asText()).append(' '));
    return Optional.of(new Poem(title, text, poet.name(), poet.id(), "https://" + language + ".wikisource.org/wiki/" + PoetDiscovery.encode(poet.sourcePage()).replace("+", "_"),
        poet.country(), poet.popularity(), "https://" + language + ".wikisource.org/wiki/" + PoetDiscovery.encode(title).replace("+", "_"),
        "https://" + language + ".wikisource.org/wiki/" + PoetDiscovery.encode(title).replace("+", "_"), categories.toString()));
  }
  private static boolean matchesPage(String href, String title) {
    if (!href.startsWith("/wiki/")) return false;
    try { return normalize(java.net.URLDecoder.decode(href.substring(href.indexOf("/wiki/") + 6), java.nio.charset.StandardCharsets.UTF_8)).equals(normalize(title)); }
    catch (RuntimeException ex) { return false; }
  }
  private static String normalize(String value) { return value.replace('_', ' ').strip().toLowerCase(Locale.ROOT); }
}
