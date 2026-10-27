package ru.ticket.text.richtext;

import java.util.List;

public record RichLine(
        List<PositionedRun> runs,
        double width,
        double height,
        double y       /* top линии */
) {}