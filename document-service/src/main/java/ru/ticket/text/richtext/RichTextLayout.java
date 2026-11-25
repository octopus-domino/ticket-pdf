package ru.ticket.text.richtext;

import ru.ticket.text.TextLayoutService;

import java.util.ArrayList;
import java.util.List;

public final class RichTextLayout {

    private RichTextLayout() {}

    private static final int PARAGRAPH_GAP_PX = 8;

    public static LayoutResult layout(
            List<RichBlock> blocks,
            double maxWidth,
            int baseFontSize,
            String baseFontFamily,
            double baseLineHeightCoef,
            TextLayoutService measure,
            TableLayoutService tableLayout
    ) {
        List<PositionedBlock> positioned = new ArrayList<>();
        double cursorY = 0;

        for (RichBlock block : blocks) {
            if (block instanceof RichBlock.Text tb) {
                List<RichLine> lines = layoutText(
                        tb.runs(), maxWidth, baseFontSize, baseFontFamily,
                        baseLineHeightCoef, measure
                );
                if (lines.isEmpty()) continue;
                positioned.add(new TextBlock(lines, cursorY));
                double h = 0;
                for (RichLine l : lines) h = Math.max(h, l.y() + l.height());
                cursorY += h; // <-- Убран paragraphGap (как в TextEl.tsx)
            } else if (block instanceof RichBlock.Table tb) {
                double h = tableLayout.measure(tb.data(), maxWidth, baseFontSize,
                        baseFontFamily, baseLineHeightCoef);
                positioned.add(new TableBlock(tb.data(), cursorY, h));
                cursorY += h; // <-- Убран paragraphGap (как в TextEl.tsx)
            }
        }

        double totalHeight = positioned.isEmpty() ? 0 : cursorY;
        return new LayoutResult(positioned, Math.max(0, totalHeight));
    }

    public static List<RichLine> layoutText(
            List<RichRun> runs, double maxWidth,
            int baseFontSize, String baseFontFamily,
            double baseLineHeightCoef, TextLayoutService measure
    ) {
        List<RichLine> lines = new ArrayList<>();
        if (runs.isEmpty() || maxWidth <= 0) return lines;

        List<PositionedRun> currentLine = new ArrayList<>();
        double cursorX = 0;
        double cursorY = 0;
        int currentLineMaxFontSize = 0;
        double leadingGapY = 0;
        int pendingNewlines = 0;

        int paragraphGap = PARAGRAPH_GAP_PX;
        double approxLh = Math.max(1, Math.round(baseFontSize * baseLineHeightCoef));

        for (RichRun run : runs) {
            if (run.isBreak()) {
                pendingNewlines += run.newlineCount();
                continue;
            }

            if (pendingNewlines > 0) {
                if (lines.isEmpty() && currentLine.isEmpty()) {
                    int pairs = pendingNewlines / 2;
                    if (pairs > 0) {
                        leadingGapY += paragraphGap;
                        for (int p = 1; p < pairs; p++) {
                            leadingGapY += approxLh + paragraphGap;
                        }
                    }
                } else {
                    cursorY = flushLine(lines, currentLine, cursorY,
                            currentLineMaxFontSize, baseLineHeightCoef);
                    currentLine = new ArrayList<>();
                    cursorX = 0;
                    currentLineMaxFontSize = 0;

                    int pairs = pendingNewlines / 2;
                    if (pairs > 0) {
                        cursorY += paragraphGap;
                        for (int p = 1; p < pairs; p++) {
                            cursorY += approxLh + paragraphGap;
                        }
                    }
                }
                pendingNewlines = 0;
            }

            for (Token t : tokenize(run.text())) {
                if (t.text().isEmpty()) continue;

                int fs = run.fontSize() != null ? run.fontSize() : baseFontSize;
                String family = run.fontFamily() != null ? run.fontFamily() : baseFontFamily;
                String style = fontStyleOf(run);

                double w = measure.measure(t.text(), family, fs, style);

                if (cursorX + w > maxWidth && cursorX > 0) {
                    cursorY = flushLine(lines, currentLine, cursorY,
                            currentLineMaxFontSize, baseLineHeightCoef);
                    currentLine = new ArrayList<>();
                    cursorX = 0;
                    currentLineMaxFontSize = 0;
                    if (t.isWhitespace()) continue;
                }

                if (leadingGapY > 0
                        && lines.isEmpty() && currentLine.isEmpty()) {
                    cursorY += leadingGapY;
                    leadingGapY = 0;
                }

                if (fs > currentLineMaxFontSize) currentLineMaxFontSize = fs;

                currentLine.add(new PositionedRun(
                        t.text(), cursorX, cursorY, w, fs, family,
                        run.bold(), run.italic(), run.underline(), run.strike(),
                        run.color(), run.bgColor()
                ));
                cursorX += w;
            }
        }

        flushLine(lines, currentLine, cursorY, currentLineMaxFontSize, baseLineHeightCoef);
        return lines;
    }

    private static double flushLine(
            List<RichLine> lines,
            List<PositionedRun> currentLine,
            double topLine,
            int maxFontSize,
            double lineHeightCoef
    ) {
        if (currentLine.isEmpty()) return topLine;

        int lineHeight = Math.max(
                maxFontSize,
                (int) Math.round(maxFontSize * lineHeightCoef)
        );

        List<PositionedRun> finalRuns = new ArrayList<>(currentLine.size());
        for (PositionedRun r : currentLine) {
            double baseline = topLine + lineHeight * 0.8
                    - (lineHeight - r.fontSize()) * 0.5;
            finalRuns.add(new PositionedRun(
                    r.text(), r.x(), baseline, r.width(),
                    r.fontSize(), r.fontFamily(),
                    r.bold(), r.italic(), r.underline(), r.strike(),
                    r.color(), r.bgColor()
            ));
        }

        double totalWidth = finalRuns.isEmpty() ? 0 :
                finalRuns.get(finalRuns.size() - 1).x()
                        + finalRuns.get(finalRuns.size() - 1).width();
        lines.add(new RichLine(finalRuns, totalWidth, lineHeight, topLine));

        return topLine + lineHeight;
    }

    private static List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        if (text.isEmpty()) return tokens;
        StringBuilder cur = new StringBuilder();
        boolean ws = Character.isWhitespace(text.charAt(0));
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean cws = Character.isWhitespace(c);
            if (cws != ws && cur.length() > 0) {
                tokens.add(new Token(cur.toString(), ws));
                cur.setLength(0);
            }
            cur.append(c);
            ws = cws;
        }
        if (cur.length() > 0) tokens.add(new Token(cur.toString(), ws));
        return tokens;
    }

    private static String fontStyleOf(RichRun r) {
        return r.bold() && r.italic() ? "italic bold"
                : r.bold() ? "bold"
                : r.italic() ? "italic"
                : "normal";
    }

    private record Token(String text, boolean isWhitespace) {}

    public record LayoutResult(List<PositionedBlock> blocks, double totalHeight) {}

    public sealed interface PositionedBlock permits TextBlock, TableBlock {
        double y();
    }

    public record TextBlock(List<RichLine> lines, double y) implements PositionedBlock {}

    public record TableBlock(RichTableData data, double y, double height) implements PositionedBlock {}
}