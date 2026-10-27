package ru.ticket.text.richtext;

import java.util.List;

public record RichTableData(
        List<RichTableCell> cells,
        int rowCount,
        int colCount,
        double[] colWidths
) {}