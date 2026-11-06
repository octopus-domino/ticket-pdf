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
 * <p><b>Policy — TSV is authoritative for structure, hOCR enriches.</b>
 * When a TSV word matches an hOCR word (IoU ≥ 0.5), geometry and line id come
 * from TSV; hOCR contributes only {@link StyleHints}. This is what restores
 * the plain-Tesseract layout quality while keeping style metadata.
 *
 * <p><b>hOCR-only words are preserved.</b> When TSV fails on a line (dense
 * small text, damaged input), hOCR is the only source of truth we have.
 * Dropping those words silently loses text. Their line ids, however, use a
 * different numbering space ({@code L1, L2, …} vs {@code page:block:par:line}),
 * so they are prefixed with {@code hocr:} to guarantee they can never collide
 * with a TSV line in {@code PageLayoutAnalyzer}'s grouping.
 *
 * <p><b>Ordering</b> is preserved as before: words sorted by (x, y).
 */
@Component
public class OcrResultFusion {

    public static final double IOU_THRESHOLD = 0.5;

    private static final String HOCR_LINE_PREFIX = "hocr:";

    public List<FusedWord> fuse(List<Word> tsv, List<Word> hocr) {
        List<FusedWord> result = new ArrayList<>(tsv.size() + hocr.size());
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

        // hOCR-only words: keep text, prefix line id, no geometry override.
        for (int i = 0; i < hocr.size(); i++) {
            if (usedHocr[i]) continue;
            Word b = hocr.get(i);
            String line = b.line() == null || b.line().isBlank()
                    ? null
                    : HOCR_LINE_PREFIX + b.line();
            result.add(new FusedWord(
                    b.text(), b.x(), b.y(), b.width(), b.height(),
                    b.confidence(), 1, hintsOf(b), false, line));
        }

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