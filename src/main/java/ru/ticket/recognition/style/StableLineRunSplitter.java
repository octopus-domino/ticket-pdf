package ru.ticket.recognition.style;

import java.awt.image.BufferedImage;
import java.util.*;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.InkStyleSampler;
import ru.ticket.recognition.OcrEngine.Word;

/** Strategy: source PDF typography is authoritative; scanned words share one conservative line style. */
@Primary
@Component
public class StableLineRunSplitter implements StyledRunSplitter {
  private static final double INK_TO_FONT_SIZE = 1.3;

  @Override
  public List<StyledRun> split(BufferedImage image, List<Word> line) {
    if (line.isEmpty()) return List.of();
    var heights = line.stream().mapToInt(Word::height).sorted().toArray();
    double size = Math.max(8, heights[heights.length / 2] * INK_TO_FONT_SIZE);
    String color = InkStyleSampler.textColor(image, line);
    var result = new ArrayList<StyledRun>();
    for (var word : line.stream().sorted(Comparator.comparingInt(Word::x)).toList()) {
      boolean nativeText = word.line() != null && word.line().startsWith("pdf:");
      var hints = word.hints();
      var style = nativeText
          ? new TextStyle(color, hints.fontSize(), Boolean.TRUE.equals(hints.bold()),
              Boolean.TRUE.equals(hints.italic()), false, false, hints.fontFamily(), true)
          : new TextStyle(color, size, false, false, false, false, "Arial", true);
      append(result, word, style);
    }
    return result;
  }

  private void append(List<StyledRun> result, Word word, TextStyle style) {
    if (result.isEmpty() || !result.get(result.size() - 1).style().equals(style)) {
      result.add(new StyledRun(word, style)); return;
    }
    var previous = result.remove(result.size() - 1).word();
    int left = Math.min(previous.x(), word.x()), top = Math.min(previous.y(), word.y());
    int right = Math.max(previous.x() + previous.width(), word.x() + word.width());
    int bottom = Math.max(previous.y() + previous.height(), word.y() + word.height());
    result.add(new StyledRun(new Word(previous.text() + " " + word.text(), left, top, right - left,
        bottom - top, Math.min(previous.confidence(), word.confidence()), previous.line(), previous.hints()), style));
  }
}
