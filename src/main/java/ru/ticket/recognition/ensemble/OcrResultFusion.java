package ru.ticket.recognition.ensemble;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.OcrEngine.StyleHints;
import ru.ticket.recognition.OcrEngine.Word;

/**
 * Merges TSV and hOCR word streams.
 *
 * <p><b>Policy — TSV is authoritative.</b> The plain-Tesseract pipeline
 * produced excellent geometry and line grouping from the start. Everything
 * that drives layout (position, bbox, line id) is taken from TSV unchanged.
 * hOCR contributes only optional {@link StyleHints} (font-family) that pixel
 * analysis cannot reliably recover; its own geometry and line ids are
 * discarded. This guarantees that enabling hOCR cannot shift a single line
 * boundary or bbox — it can only enrich style metadata.
 *
 * <ol>
 *   <li>For each TSV word, find the best hOCR match by IoU ≥ 0.5.</li>
 *   <li>On match: keep TSV geometry and line; merge hints; boost confidence
 *       on text agreement.</li>
 *   <li>On no match: keep the TSV word as-is.</li>
 *   <li>Leftover hOCR-only words are deliberately <em>not</em> appended.
 *       Their line ids belong to a different numbering space and would
 *       create phantom lines; dropping them keeps the layout graph clean.</li>
 * </ol>
 */
@Component
public class OcrResultFusion {

    public static final double IOU_THRESHOLD = 0.5;

    public List<FusedWord> fuse(List<Word> tsv, List<Word> hocr) {
        List<FusedWord> result = new ArrayList<>(tsv.size());
        boolean[] usedHocr = new boolean[hocr.size()];

        for (Word a : tsv) {
            int match = findBestMatch(a, hocr, usedHocr);
            if (match < 0) {
                result.add(new FusedWord(
                        a.text(), a.x(), a.y(), a.width(), a.height(),
                        a.confidence(), 1, hintsOf(a), false, a.line()));
                continue;
            }
            Word b = hocr.get(match);
            usedHocr[match] = true;
            result.add(mergePair(a, b));
        }
        // Intentionally do NOT iterate over remaining hOCR words.

        result.sort(Comparator.comparingInt(FusedWord::x).thenComparingInt(FusedWord::y));
        return result;
    }

    private FusedWord mergePair(Word tsv, Word hocr) {
        boolean sameText = tsv.text().equals(hocr.text());
        double combined = sameText
                ? Math.min(100, (tsv.confidence() + hocr.confidence()) / 2.0 + 5)
                : Math.max(tsv.confidence(), hocr.confidence());

        String text = sameText
                ? tsv.text()
                : (tsv.confidence() >= hocr.confidence() ? tsv.text() : hocr.text());

        StyleHints hints = hintsOf(hocr);
        if (hints == StyleHints.NONE) hints = hintsOf(tsv);

        // TSV is the source of truth for x/y/w/h/line. hOCR only feeds hints.
        return new FusedWord(
                text,
                tsv.x(), tsv.y(), tsv.width(), tsv.height(),
                combined, 2, hints, !sameText,
                tsv.line());
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