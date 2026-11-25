package ru.ticket.text.richtext;

import java.util.List;

public record RichTableCell(
        List<RichBlock> blocks,
        boolean isHeader,
        int colspan,
        int rowspan,
        int rowIndex,
        int colIndex,
        double[] rawWidths,
        String bgColor,

        // Legacy borders (используются как дефолт, если ни одна per-side не задана).
        String borderColor,
        Double borderWidth,
        String borderStyle,

        // Per-side borders из inline CSS. null = не задано в HTML.
        BorderSide borderTop,
        BorderSide borderRight,
        BorderSide borderBottom,
        BorderSide borderLeft,

        RichTableCellPadding padding,
        String align,
        String verticalAlign
) {
    public RichTableCellPadding paddingOrDefault() {
        return padding != null ? padding : RichTableCellPadding.DEFAULT;
    }

    /** true, если в HTML были явные стили границ (хоть по одной стороне). */
    public boolean hasExplicitBorders() {
        return borderTop != null || borderRight != null
                || borderBottom != null || borderLeft != null;
    }

    /** true, если хоть одна сторона реально должна быть нарисована. */
    public boolean hasAnyVisibleBorder() {
        return (borderTop    != null && borderTop.isVisible())
                || (borderRight  != null && borderRight.isVisible())
                || (borderBottom != null && borderBottom.isVisible())
                || (borderLeft   != null && borderLeft.isVisible());
    }
}