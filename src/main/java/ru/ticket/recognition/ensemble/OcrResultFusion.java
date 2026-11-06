package ru.ticket.recognition.ensemble;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.OcrEngine.StyleHints;
import ru.ticket.recognition.OcrEngine.Word;

/**
 * Сливает два списка слов (TSV и hOCR) в один.
 *
 * <ol>
 *   <li>Жадное выравнивание по IoU bbox (порог 0.5).</li>
 *   <li>Совпавшие пары → confidence-взвешенное голосование за текст;
 *       геометрия = union bbox; hints берутся из hOCR-слова.</li>
 *   <li>Несовпавшие слова добавляются как есть (sources=1).</li>
 * </ol>
 */
@Component
public class OcrResultFusion {

    public static final double IOU_THRESHOLD = 0.5;

    public List<FusedWord> fuse(List<Word> tsv, List<Word> hocr) {
        List<FusedWord> result = new ArrayList<>();
        boolean[] usedHocr = new boolean[hocr.size()];

        for (Word a : tsv) {
            int match = findBestMatch(a, hocr, usedHocr);
            if (match < 0) {
                result.add(new FusedWord(a.text(), a.x(), a.y(), a.width(), a.height(),
                        a.confidence(), 1, hintsOf(a), false));
                continue;
            }
            Word b = hocr.get(match);
            usedHocr[match] = true;
            result.add(mergePair(a, b));
        }

        for (int i = 0; i < hocr.size(); i++) {
            if (usedHocr[i]) continue;
            Word b = hocr.get(i);
            result.add(new FusedWord(b.text(), b.x(), b.y(), b.width(), b.height(),
                    b.confidence(), 1, hintsOf(b), false));
        }

        result.sort(Comparator.comparingInt(FusedWord::x).thenComparingInt(FusedWord::y));
        return result;
    }

    private FusedWord mergePair(Word a, Word b) {
        boolean sameText = a.text().equals(b.text());
        double combined = sameText
                ? Math.min(100, (a.confidence() + b.confidence()) / 2.0 + 5)
                : Math.max(a.confidence(), b.confidence());

        int x = Math.min(a.x(), b.x());
        int y = Math.min(a.y(), b.y());
        int right = Math.max(a.x() + a.width(), b.x() + b.width());
        int bottom = Math.max(a.y() + a.height(), b.y() + b.height());

        String text = sameText
                ? a.text()
                : (a.confidence() >= b.confidence() ? a.text() : b.text());

        StyleHints hints = hintsOf(b);
        if (hints == StyleHints.NONE) hints = hintsOf(a);

        return new FusedWord(text, x, y, right - x, bottom - y,
                combined, 2, hints, !sameText);
    }

    private static StyleHints hintsOf(Word word) {
        return word.hints() == null ? StyleHints.NONE : word.hints();
    }

    private int findBestMatch(Word target, List<Word> candidates, boolean[] used) {
        int best = -1;
        double bestIou = IOU_THRESHOLD;
        for (int i = 0; i < candidates.size(); i++) {
            if (used[i]) continue;
            double iou = iou(target, candidates.get(i));
            if (iou > bestIou) {
                bestIou = iou;
                best = i;
            }
        }
        return best;
    }

    static double iou(Word a, Word b) {
        int left = Math.max(a.x(), b.x());
        int top = Math.max(a.y(), b.y());
        int right = Math.min(a.x() + a.width(), b.x() + b.width());
        int bottom = Math.min(a.y() + a.height(), b.y() + b.height());
        if (right <= left || bottom <= top) return 0;
        double inter = (double) (right - left) * (bottom - top);
        double union = (double) a.width() * a.height()
                + (double) b.width() * b.height() - inter;
        return union <= 0 ? 0 : inter / union;
    }
}