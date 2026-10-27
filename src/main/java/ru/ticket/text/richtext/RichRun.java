package ru.ticket.text.richtext;

public record RichRun(
        String text,
        boolean bold,
        boolean italic,
        boolean underline,
        boolean strike,
        String color,
        String bgColor,
        Integer fontSize,
        String fontFamily,
        boolean blockBreak
) {
    public static RichRun lineBreak() {
        return new RichRun("\n", false, false, false, false, null, null, null, null, false);
    }

    public static RichRun paragraphBreak() {
        return new RichRun("\n\n", false, false, false, false, null, null, null, null, true);
    }

    public boolean isBreak() {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // Игнорируем \n, неразрывный пробел (\u00A0), zero-width space (\u200B) и BOM (\uFEFF)
            if (c != '\n' && c != '\u200B' && c != '\uFEFF' && c != '\u00A0' && !Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }

    public int newlineCount() {
        if (!isBreak()) return 0;
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }
}