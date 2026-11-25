package ru.ticket.text.richtext;

public record RichTableCellPadding(
        double top, double right, double bottom, double left
) {
    /*
     * Синхронизировано с CSS:
     *   `.rt-content .ProseMirror td/th { padding: 8px 10px; }`
     */
    public static final RichTableCellPadding DEFAULT = new RichTableCellPadding(8, 10, 8, 10);
}