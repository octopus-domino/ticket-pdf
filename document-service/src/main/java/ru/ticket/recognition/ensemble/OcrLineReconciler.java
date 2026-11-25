package ru.ticket.recognition.ensemble;

import java.util.*;
import ru.ticket.recognition.OcrEngine.Word;

/** Assigns missing sparse-pass labels to geometric lines instead of discarding them or scattering text. */
public final class OcrLineReconciler {
  private OcrLineReconciler() {}

  public static List<Word> reconcile(List<FusedWord> fused, List<Word> primary) {
    var result = new ArrayList<Word>();
    for (var item : fused) {
      var word = item.toWord(null);
      if (word.line() == null || !word.line().startsWith("hocr:")) { result.add(word); continue; }
      if (word.confidence() < 45 || primary.stream().anyMatch(existing -> covered(existing, word))) continue;
      var neighbour = primary.stream().filter(w -> sameBaseline(w, word))
          .min(Comparator.comparingInt(w -> Math.abs(w.x() - word.x())));
      String line = neighbour.map(Word::line).orElse("sparse:" + Math.round((word.y() + word.height()) / 4.0));
      result.add(new Word(word.text(), word.x(), word.y(), word.width(), word.height(), word.confidence(), line));
    }
    return result;
  }

  /** Different segmentation of the same ink is not an additional label. */
  private static boolean covered(Word first, Word second) {
    int width = Math.max(0, Math.min(first.x() + first.width(), second.x() + second.width()) - Math.max(first.x(), second.x()));
    int height = Math.max(0, Math.min(first.y() + first.height(), second.y() + second.height()) - Math.max(first.y(), second.y()));
    double smaller = Math.min((double) first.width() * first.height(), (double) second.width() * second.height());
    return smaller > 0 && width * height / smaller >= .45;
  }

  private static boolean sameBaseline(Word first, Word second) {
    return Math.abs(first.y() + first.height() - second.y() - second.height())
        <= Math.max(3, Math.min(first.height(), second.height()) * .25)
        && Math.abs(first.x() - second.x()) <= Math.max(100, first.width() + second.width() + 40);
  }
}
