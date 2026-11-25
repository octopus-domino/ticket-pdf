package ru.ticket.recognition.ensemble;

import ru.ticket.recognition.OcrEngine.StyleHints;
import ru.ticket.recognition.OcrEngine.Word;

/**
 * Result of merging two OCR passes.
 *
 * <p><b>Contract:</b> {@code line} is authoritative and comes from TSV.
 * TSV emits stable {@code page:block:par:line} identifiers that drive the
 * one-text-element-per-visual-line grouping in {@code PageLayoutAnalyzer}.
 * Losing this field collapses the whole page into a single text element — the
 * exact regression this record was extended to fix. hOCR line ids have a
 * different format ({@code L1}, {@code L2}, …) and must never leak here.
 *
 * <p>{@link #toWord(String)} is defensive: if {@code line} is missing for any
 * reason, it falls back to a per-word y-bucket (never a shared constant) so
 * that a single lost id cannot re-collapse the layout.
 *
 * <p><b>Backward compatibility:</b> the 9-argument constructor (without
 * {@code line}) is retained for tests and legacy callers; {@code line} will
 * be {@code null} and {@code toWord} will use the supplied fallback.
 */
public record FusedWord(
        String text,
        int x, int y, int width, int height,
        double confidence,
        int sources,
        StyleHints hints,
        boolean disagreement,
        String line) {

    /** Legacy 9-arg form (no line). Delegates with {@code line = null}. */
    public FusedWord(String text,
                     int x, int y, int width, int height,
                     double confidence,
                     int sources,
                     StyleHints hints,
                     boolean disagreement) {
        this(text, x, y, width, height, confidence, sources, hints, disagreement, null);
    }

    public Word toWord(String fallbackLineId) {
        String effective = line;
        if (effective == null || effective.isBlank()) {
            effective = fallbackLineId;
        }
        if (effective == null || effective.isBlank()) {
            // Last-resort bucketing — prevents a lost line id from turning
            // the entire page into one text element.
            int baseline = y + (int) Math.round(height * 0.8);
            effective = "ybucket:" + (baseline / 8);
        }
        return new Word(
                text,
                x, y, width, height,
                confidence,
                effective,
                hints == null ? StyleHints.NONE : hints);
    }
}