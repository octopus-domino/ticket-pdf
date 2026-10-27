package ru.ticket.text.richtext;

import org.jsoup.nodes.Element;

import java.util.HashMap;
import java.util.Map;

/**
 * Стиль, унаследованный от родительских тегов и inline-атрибутов.
 * Immutable: каждый extend() возвращает новый scope.
 *
 * Inline-стили читаются из атрибута style="..." вручную, потому что
 * у org.jsoup.nodes.Element нет метода .style() — это стороннее
 * расширение, а не часть стандартного jsoup.
 */
final class StyleScope {

    static final StyleScope EMPTY = new StyleScope(
            null, null, null, null, false, false, false, false
    );

    private final String color;
    private final String bgColor;
    private final Integer fontSize;
    private final String fontFamily;
    private final boolean bold;
    private final boolean italic;
    private final boolean underline;
    private final boolean strike;

    private StyleScope(String color, String bgColor, Integer fontSize, String fontFamily,
                       boolean bold, boolean italic, boolean underline, boolean strike) {
        this.color = color;
        this.bgColor = bgColor;
        this.fontSize = fontSize;
        this.fontFamily = fontFamily;
        this.bold = bold;
        this.italic = italic;
        this.underline = underline;
        this.strike = strike;
    }

    StyleScope extend(Element el) {
        String c = color;
        String b = bgColor;
        Integer fs = fontSize;
        String ff = fontFamily;
        boolean bo = bold;
        boolean it = italic;
        boolean un = underline;
        boolean st = strike;

        String tag = el.tagName().toLowerCase();

        switch (tag) {
            case "b", "strong" -> bo = true;
            case "i", "em"     -> it = true;
            case "u"           -> un = true;
            case "s", "strike", "del" -> st = true;
            case "a"           -> { un = true; if (c == null) c = "#2563eb"; }
            case "mark"        -> {
                String bg = el.attr("data-color");
                if (bg != null && !bg.isBlank()) b = bg.trim();
            }
            case "h1" -> { bo = true; if (fs == null) fs = 28; }
            case "h2" -> { bo = true; if (fs == null) fs = 22; }
            case "h3" -> { bo = true; if (fs == null) fs = 18; }
        }

        /* Inline-стили имеют приоритет над тегом */
        Map<String, String> inline = parseInlineStyle(el.attr("style"));

        String sColor = inline.get("color");
        if (sColor != null && !sColor.isBlank()) c = sColor;

        String sBg = inline.get("background-color");
        if (sBg != null && !sBg.isBlank()) b = sBg;

        String sFs = inline.get("font-size");
        if (sFs != null) {
            Integer parsed = parsePx(sFs);
            if (parsed != null) fs = parsed;
        }

        String sFf = inline.get("font-family");
        if (sFf != null && !sFf.isBlank()) {
            ff = sFf.split(",")[0].replace("\"", "").replace("'", "").trim();
        }

        String fw = inline.get("font-weight");
        if (fw != null && (fw.equalsIgnoreCase("bold") || isNumericAndAtLeast(fw, 600))) {
            bo = true;
        }

        String fst = inline.get("font-style");
        if (fst != null && fst.contains("italic")) it = true;

        String td = inline.get("text-decoration");
        if (td != null) {
            if (td.contains("underline")) un = true;
            if (td.contains("line-through")) st = true;
        }

        return new StyleScope(c, b, fs, ff, bo, it, un, st);
    }

    RichRun toRun(String text) {
        return new RichRun(text, bold, italic, underline, strike,
                color, bgColor, fontSize, fontFamily, false);
    }

    /* ============================================================
       Парсинг inline style="..."
       ============================================================
       Возвращает map { "color" -> "red", "font-size" -> "16px" }.
       Значения обрезаются от пробелов. Пустые пары пропускаются.
    */
    private static Map<String, String> parseInlineStyle(String style) {
        Map<String, String> result = new HashMap<>();
        if (style == null || style.isBlank()) return result;

        for (String part : style.split(";")) {
            int colon = part.indexOf(':');
            if (colon <= 0) continue;

            String key = part.substring(0, colon).trim().toLowerCase();
            String val = part.substring(colon + 1).trim();

            if (!key.isEmpty() && !val.isEmpty()) {
                result.put(key, val);
            }
        }
        return result;
    }

    /* ============================================================ */

    private static Integer parsePx(String v) {
        try {
            String s = v.replace("px", "").trim();
            double d = Double.parseDouble(s);
            return (int) Math.round(d);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isNumericAndAtLeast(String v, int min) {
        try {
            return Integer.parseInt(v.trim()) >= min;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}