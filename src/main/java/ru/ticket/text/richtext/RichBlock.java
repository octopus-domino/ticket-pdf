package ru.ticket.text.richtext;

import java.util.List;

public sealed interface RichBlock permits RichBlock.Text, RichBlock.Table {

    record Text(List<RichRun> runs) implements RichBlock {}

    record Table(RichTableData data) implements RichBlock {}

    static Text ofText(List<RichRun> runs) { return new Text(runs); }
    static Table ofTable(RichTableData data) { return new Table(data); }
}