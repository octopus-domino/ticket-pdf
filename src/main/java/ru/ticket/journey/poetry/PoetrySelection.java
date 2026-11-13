package ru.ticket.journey.poetry;

import java.util.*;
import ru.ticket.common.ApiException;

/** Transparent thematic ranking of real source texts. Weather influences the mood, not the card's subject. */
public final class PoetrySelection {
  private PoetrySelection() {}
  private record Words(String warm, String joy, String hope, String memory, String nature, String humor, String harsh) {}
  private static final Map<String,Words> WORDS = Map.of(
      "ru", new Words("любов сердц друг нежн уют тепл", "радост смех свет улыб счаст", "надежд мечт весн жизнь завтра", "памят вспом детств родн дом семь", "птиц цвет лес сад солн руч", "шут смеш смех басн эпиграм юмор", "кров убий смерт могил казн разврат"),
      "en", new Words("love heart friend gentle warm home", "joy laugh light smile happy", "hope dream spring life tomorrow", "memory remember childhood family home", "bird flower forest garden sun river", "funny humour humor laughter comic fable", "blood murder death grave lust torture"),
      "zh", new Words("爱 心 友 柔 暖 家", "喜 欢 笑 乐 光", "望 梦 春 生 明", "忆 思 故 亲 家", "鸟 花 林 园 日 水", "笑 趣 诙 谐 寓 言", "血 杀 死 墓 刑 淫"),
      "pl", new Words("miło serc przyja ciep dom", "rado śmiech świat uśmie szczę", "nadzie marze wios życi jutro", "pamię wspomn dzieci rodzin dom", "ptak kwiat las ogród słoń rzek", "żart śmies śmiech humor bajk", "krew morder śmier grob tortur"),
      "it", new Words("amore cuore amic dolce calor casa", "gioia riso luce sorr felic", "sper sogno primavera vita domani", "ricord memori infanz famigl casa", "uccell fior bosco giardin sole fium", "scherz divert risat umor favol", "sangue uccis morte tomba lussur tortur"),
      "es", new Words("amor corazón amig dulce calor hogar", "alegr risa luz sonri feliz", "esper sueño primavera vida mañana", "recuer memor infancia familia hogar", "pájar flor bosque jardín sol río", "broma cómic humor risa fábul", "sangre asesinat muerte tumba lujur tortur"),
      "nl", new Words("lief hart vriend zacht warm thuis", "vreug lach licht glim geluk", "hoop droom lente leven morgen", "herinner jeugd familie thuis", "vogel bloem bos tuin zon rivier", "grap grapp lach humor fabel", "bloed moord dood graf lust martel"),
      "fr", new Words("amour cœur ami doux chaleur foyer", "joie rire lumière souri heureux", "espoir rêve printemps vie demain", "souvenir mémoire enfance famille maison", "oiseau fleur forêt jardin soleil rivière", "drôle plaisant rire humour fable", "sang meurtre mort tombe luxur tortur"),
      "de", new Words("liebe herz freund sanft warm heim", "freude lach licht lächel glück", "hoff träum frühling leben morgen", "erinner kindheit familie heim", "vogel blume wald garten sonne fluss", "scherz lustig lach humor fabel", "blut mord tod grab folter"));
  public static void validate(String language, String mood, String audience) {
    if (language == null || mood == null || audience == null || !WORDS.containsKey(language) || !Set.of("heartfelt", "funny").contains(mood)
        || !Set.of("child", "teen", "youth", "middle", "older").contains(audience)) throw ApiException.badRequest("journey.poetry.preferences");
  }
  public static String hint(String language, String mood, String audience) {
    var words = WORDS.get(language);
    if (words == null) throw ApiException.badRequest("journey.poetry.language");
    if(audience.equals("child")) return switch(language) {
      case "ru"->"детск колыбельн"; case "en"->"children nursery lullaby"; case "zh"->"童 儿 兒";
      case "pl"->"dzieci kołysank"; case "it"->"bambin ninna"; case "es"->"infantil niño nana";
      case "nl"->"kinder wiegelied"; case "fr"->"enfant berceuse"; default->"kinder wiegenlied";
    };
    return mood.equals("funny") ? words.humor : words.warm;
  }
  public static List<WikisourcePoems.Poem> select(List<WikisourcePoems.Poem> poems, List<PoetryWeatherService.Day> days,
      String language, String mood, String audience, String seed) {
    validate(language, mood, audience);
    if (days.size() != 7) throw ApiException.badRequest("journey.poetry.weather-unavailable");
    var remaining = new ArrayList<>(poems.stream().filter(poem -> suitable(poem, language, mood, audience)).toList());
    if (remaining.size() < 7) throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "journey.poetry.not-enough");
    var selected = new ArrayList<WikisourcePoems.Poem>(); var usedAuthors = new HashMap<String,Integer>();
    var random = new SplittableRandom(Objects.hash(seed, language, mood, audience, days.get(0).date()));
    for (var day : days) {
      var jitter = new IdentityHashMap<WikisourcePoems.Poem,Double>(); remaining.forEach(poem -> jitter.put(poem, random.nextDouble(4)));
      var best = remaining.stream().max(Comparator.comparingDouble(poem -> score(poem, day, language, audience)
          + jitter.get(poem) - usedAuthors.getOrDefault(poem.authorId(), 0) * 3)).orElseThrow();
      selected.add(best); remaining.remove(best); usedAuthors.merge(best.authorId(), 1, Integer::sum);
    }
    return List.copyOf(selected);
  }
  static boolean suitable(WikisourcePoems.Poem poem, String language, String mood, String audience) {
    var words = WORDS.get(language); String text = (poem.title() + " " + poem.text() + " " + poem.categories()).toLowerCase(Locale.ROOT);
    if (hits(text, words.harsh) > 0) return false;
    if(audience.equals("child")) {
      // A short adult lyric or moral fable is not evidence of suitability for children.
      String genre=(poem.categories()+" "+poem.title()).toLowerCase(Locale.ROOT);
      String evidence=switch(language) {
        case "ru"->"детск|для детей|колыбельн"; case "en"->"children|nursery|lullab";
        case "zh"->"兒童|儿童|童謠|童谣|搖籃|摇篮"; case "pl"->"dzieci|kołysank";
        case "it"->"bambin|infantil|ninna"; case "es"->"infantil|niños|niñas|canción de cuna";
        case "nl"->"kinder|wiegelied"; case "fr"->"enfant|berceuse"; default->"kinder|wiegenlied";
      };
      if(!java.util.regex.Pattern.compile(evidence).matcher(genre).find()) return false;
    }
    if (mood.equals("funny") && hits(text, words.humor) == 0) return false;
    int max = audience.equals("child") ? (language.equals("zh") ? 400 : 800) : audience.equals("teen") ? 1200 : 1600;
    return poem.text().length() <= max;
  }
  private static double score(WikisourcePoems.Poem poem, PoetryWeatherService.Day day, String language, String audience) {
    var words = WORDS.get(language); String text = (poem.title() + " " + poem.text()).toLowerCase(Locale.ROOT);
    double result = Math.log1p(Math.max(0, poem.popularity())) / 3;
    result += switch (audience) {
      case "child" -> hits(text, words.nature) * 2 + hits(text, words.joy) * 2;
      case "teen" -> hits(text, words.hope) * 2 + hits(text, words.warm);
      case "youth" -> hits(text, words.warm) * 2 + hits(text, words.hope);
      case "middle" -> hits(text, words.warm) * 2 + hits(text, words.memory);
      default -> hits(text, words.memory) * 2 + hits(text, words.hope) + hits(text, words.warm);
    };
    if(day.symbol().isBlank()||!Double.isFinite(day.temperature()))return result;
    boolean cosy = day.symbol().contains("rain") || day.symbol().contains("snow") || day.temperature() < 5;
    return result + (cosy ? hits(text, words.warm) * 2 + hits(text, words.hope) : hits(text, words.joy) * 2 + hits(text, words.nature));
  }
  private static int hits(String text, String words) { return (int)Arrays.stream(words.split(" ")).filter(text::contains).count(); }
}
