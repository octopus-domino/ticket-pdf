package ru.ticket.recognition;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.RecognitionDraft.Item;
import ru.ticket.recognition.RuleDetector.Rule;
import ru.ticket.recognition.layout.ArtworkDetector;
import ru.ticket.recognition.layout.RuledTableDetector;
import ru.ticket.recognition.layout.TextElementBuilder;
import ru.ticket.recognition.layout.TextMask;

/**
 * Оркестратор детерминированного разбора растрового документа.
 *
 * <ol>
 *   <li>Обнаружение таблиц ({@link RuledTableDetector}).</li>
 *   <li>Сборка текстовых элементов ({@link TextElementBuilder}).</li>
 *   <li>Свободные линии вне таблиц.</li>
 *   <li>Artwork-эвристика ({@link ArtworkDetector}) — только если вызывающий
 *       код не подменил её своим {@code ShapeRecognizer}.</li>
 * </ol>
 */
@Component
public class PageLayoutAnalyzer {

  private static final int MAX_ITEMS = 300;

  private final RuledTableDetector tableDetector;
  private final TextElementBuilder textBuilder;
  private final ArtworkDetector artworkDetector;

  public PageLayoutAnalyzer(
          RuledTableDetector tableDetector,
          TextElementBuilder textBuilder,
          ArtworkDetector artworkDetector) {
    this.tableDetector = tableDetector;
    this.textBuilder = textBuilder;
    this.artworkDetector = artworkDetector;
  }

  public RecognitionDraft analyze(BufferedImage image, List<OcrEngine.Word> words) {
    return analyze(image, words, true);
  }

  public RecognitionDraft analyze(
          BufferedImage image, List<OcrEngine.Word> words, boolean includeArtwork) {

    int width = image.getWidth();
    int height = image.getHeight();
    var items = new ArrayList<Item>();
    var warnings = new ArrayList<String>();

    List<Rule> horizontal = RuleDetector.detect(image, false);
    List<Rule> vertical = RuleDetector.detect(image, true);

    var tableResult = tableDetector.detect(image, words, horizontal, vertical);
    tableResult.table().ifPresent(items::add);

    boolean[] excluded = TextMask.fromOcrWords(width, height, words);
    for (Item item : items) {
      TextMask.paint(excluded, width, height,
              (int) item.x() - 2, (int) item.y() - 2,
              (int) item.width() + 4, (int) item.height() + 4);
    }

    Set<OcrEngine.Word> consumed = tableResult.consumedWords();
    var lines = new LinkedHashMap<String, List<OcrEngine.Word>>();
    for (var word : words) {
      if (consumed.contains(word)) continue;
      lines.computeIfAbsent(word.line(), k -> new ArrayList<>()).add(word);
    }
    for (List<OcrEngine.Word> line : lines.values()) {
      Item item = textBuilder.build(image, line, width, height);
      if (item != null) items.add(item);
    }

    for (Rule rule : tableResult.remainingHorizontal()) {
      items.add(new Item("line",
              rule.x(), rule.y() + (rule.height() - 1) / 2.0,
              rule.width(), 1,
              Map.of(
                      "stroke", InkStyleSampler.ruleColor(
                              image, rule.x(), rule.y(), rule.width(), rule.height()),
                      "strokeWidth", rule.height())));
      TextMask.paint(excluded, width, height,
              rule.x() - 2, rule.y() - 2, rule.width() + 4, rule.height() + 4);
    }
    for (Rule rule : tableResult.remainingVertical()) {
      items.add(new Item("line",
              rule.x() + (rule.width() - 1) / 2.0, rule.y(),
              1, rule.height(),
              Map.of(
                      "stroke", InkStyleSampler.ruleColor(
                              image, rule.x(), rule.y(), rule.width(), rule.height()),
                      "strokeWidth", rule.width())));
      TextMask.paint(excluded, width, height,
              rule.x() - 2, rule.y() - 2, rule.width() + 4, rule.height() + 4);
    }

    if (includeArtwork) {
      for (Item artworkItem : artworkDetector.detect(image, excluded)) {
        items.add(0, artworkItem);
      }
    }

    if (words.isEmpty()) warnings.add("recognition.no_text");
    if (words.stream().anyMatch(w -> w.confidence() < 65)) {
      warnings.add("recognition.low_confidence");
    }
    warnings.add("recognition.review_required");

    if (items.size() > MAX_ITEMS) {
      throw ru.ticket.common.ApiException.badRequest("recognition.too_complex");
    }
    return new RecognitionDraft(width, height, items, warnings, "ocr");
  }
}