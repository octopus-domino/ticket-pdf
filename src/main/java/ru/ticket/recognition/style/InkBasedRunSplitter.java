package ru.ticket.recognition.style;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.InkStyleSampler;
import ru.ticket.recognition.OcrEngine.Word;

/** Groups adjacent words with compatible styles; tolerates small font-size fitting noise. */
@Component
public class InkBasedRunSplitter implements StyledRunSplitter {

  private final TextStyleAnalyzer styleAnalyzer;

  public InkBasedRunSplitter(TextStyleAnalyzer styleAnalyzer) {
    this.styleAnalyzer = styleAnalyzer;
  }

  @Override
  public List<StyledRun> split(BufferedImage image, List<Word> line) {
    List<StyledRun> result = new ArrayList<>();
    if (line == null || line.isEmpty()) return result;

    List<Word> sorted = new ArrayList<>(line);
    sorted.sort(Comparator.comparingInt(Word::x));

    TextStyle lineStyle = null;
    if (sorted.size() > 1) {
      Word lineBounds = sorted.get(0);
      for (int i = 1; i < sorted.size(); i++) lineBounds = mergeWords(lineBounds, sorted.get(i));
      try {
        TextStyle estimate = styleAnalyzer.analyze(image, lineBounds);
        if (estimate.reliable()) lineStyle = estimate;
      } catch (RuntimeException ignored) {
        // A line estimate is optional; each word still has its original OCR bounds.
      }
    }

    Word currentWord = null;
    TextStyle currentStyle = null;

    for (Word word : sorted) {
      TextStyle style;
      try {
        style = styleAnalyzer.analyze(image, word);
      } catch (RuntimeException error) {
        style =
            TextStyle.defaultFor(
                InkStyleSampler.textColor(image, List.of(word)), Math.max(8, word.height() * 0.9));
      }

      if (lineStyle != null
          && (!style.reliable() || word.text().length() <= 3)
          && Math.abs(style.fontSize() - lineStyle.fontSize())
              <= Math.max(3, lineStyle.fontSize() * .3)
          && similarColor(style.color(), lineStyle.color())) {
        style =
            new TextStyle(
                style.color(),
                lineStyle.fontSize(),
                lineStyle.bold(),
                lineStyle.italic(),
                style.underline(),
                style.strike(),
                lineStyle.fontFamily(),
                true);
      }

      if (currentWord == null || differs(currentStyle, style)) {
        if (currentWord != null) {
          result.add(new StyledRun(currentWord, currentStyle));
        }
        currentWord = word;
        currentStyle = style;
      } else {
        currentWord = mergeWords(currentWord, word);
      }
    }
    if (currentWord != null) {
      result.add(new StyledRun(currentWord, currentStyle));
    }
    return result;
  }

  /** Ignore small rasterization differences while retaining real emphasis changes. */
  private static boolean differs(TextStyle a, TextStyle b) {
    if (a == null || b == null) return true;
    if (!similarColor(a.color(), b.color())) return true;
    if (Math.abs(a.fontSize() - b.fontSize())
        > Math.max(2, Math.min(a.fontSize(), b.fontSize()) * .25)) return true;
    if (a.bold() != b.bold()) return true;
    if (a.italic() != b.italic()) return true;
    if (a.underline() != b.underline()) return true;
    if (a.strike() != b.strike()) return true;
    return !a.fontFamily().equals(b.fontFamily());
  }

  private static boolean similarColor(String a, String b) {
    if (a.equalsIgnoreCase(b)) return true;
    if (!a.matches("#[a-fA-F0-9]{6}") || !b.matches("#[a-fA-F0-9]{6}")) return false;
    int first = Integer.parseInt(a.substring(1), 16);
    int second = Integer.parseInt(b.substring(1), 16);
    int distance = 0;
    for (int shift : new int[] {0, 8, 16}) {
      int delta = ((first >> shift) & 255) - ((second >> shift) & 255);
      distance += delta * delta;
    }
    return distance <= 24 * 24;
  }

  private static Word mergeWords(Word a, Word b) {
    int x = Math.min(a.x(), b.x());
    int y = Math.min(a.y(), b.y());
    int right = Math.max(a.x() + a.width(), b.x() + b.width());
    int bottom = Math.max(a.y() + a.height(), b.y() + b.height());
    String text = a.text() + " " + b.text();
    double conf = (a.confidence() + b.confidence()) / 2.0;
    // Merged run keeps the original line id — it is what drives layout.
    return new Word(text, x, y, right - x, bottom - y, conf, a.line());
  }
}
