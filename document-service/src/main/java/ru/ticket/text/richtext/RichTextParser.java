package ru.ticket.text.richtext;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RichTextParser {

    private static final boolean DEBUG_BORDERS = false;

    private static final Pattern RGB_PATTERN = Pattern.compile(
            "rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*([\\d.]+))?\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );

    public List<RichBlock> parse(String html) {
        if (html == null || html.isBlank()) return List.of();
        Document doc = Jsoup.parseBodyFragment(html);
        List<RichBlock> blocks = parseNodes(new ArrayList<>(doc.body().childNodes()));

        // Убираем пустые текстовые блоки (бывает от <p></p> вокруг таблиц)
        List<RichBlock> out = new ArrayList<>(blocks.size());
        for (RichBlock b : blocks) {
            if (b instanceof RichBlock.Text tb && isAllBreak(tb.runs())) continue;
            out.add(b);
        }
        return out;
    }

    public List<RichBlock> parseNodes(List<Node> nodes) {
        List<RichBlock> blocks = new ArrayList<>();
        List<RichRun> currentRuns = new ArrayList<>();
        for (Node node : nodes) {
            walk(node, StyleScope.EMPTY, currentRuns, blocks);
        }
        flushRuns(blocks, currentRuns);
        return blocks;
    }

    private static boolean isAllBreak(List<RichRun> runs) {
        for (RichRun r : runs) {
            if (!r.isBreak()) return false;
        }
        return true;
    }

    private void walk(Node node, StyleScope scope, List<RichRun> runs, List<RichBlock> blocks) {
        if (node instanceof TextNode tn) {
            String text = tn.getWholeText();
            if (!text.isEmpty()) runs.add(scope.toRun(text));
            return;
        }
        if (!(node instanceof Element el)) return;

        String tag = el.tagName().toLowerCase();

        if (tag.equals("table")) {
            flushRuns(blocks, runs);
            RichTableData td = parseTable(el, scope);
            if (td != null && !td.cells().isEmpty()) {
                blocks.add(RichBlock.ofTable(td));
            }
            return;
        }

        if (tag.equals("br")) {
            if (!runs.isEmpty() && runs.get(runs.size() - 1).isBreak()) return;
            runs.add(RichRun.lineBreak());
            return;
        }

        StyleScope next = scope.extend(el);

        if (tag.equals("ul") || tag.equals("ol")) {
            walkList(el, next, runs, blocks);
            return;
        }

        if (isBlockTag(tag)) {
            boolean wasEmpty = el.text().trim().isEmpty();
            if (!runs.isEmpty() || wasEmpty) {
                runs.add(RichRun.paragraphBreak());
            }
            for (Node child : el.childNodes()) {
                walk(child, next, runs, blocks);
            }
            return;
        }

        for (Node child : el.childNodes()) {
            walk(child, next, runs, blocks);
        }
    }

    private void walkList(Element listEl, StyleScope scope, List<RichRun> runs, List<RichBlock> blocks) {
        if (!runs.isEmpty()) runs.add(RichRun.paragraphBreak());
        boolean ordered = "ol".equals(listEl.tagName());
        int idx = 1;

        for (Element li : listEl.children()) {
            if (!li.tagName().equalsIgnoreCase("li")) continue;

            String prefix = ordered ? (idx++ + ". ") : "• ";
            runs.add(scope.toRun(prefix));

            boolean first = true;
            for (Node child : li.childNodes()) {
                if (child instanceof TextNode tn) {
                    String t = tn.getWholeText();
                    if (!t.isEmpty()) {
                        runs.add(scope.toRun(t));
                        first = false;
                    }
                    continue;
                }
                if (!(child instanceof Element el)) continue;

                String tag = el.tagName().toLowerCase();

                if (tag.equals("table")) {
                    flushRuns(blocks, runs);
                    RichTableData td = parseTable(el, scope);
                    if (td != null && !td.cells().isEmpty()) {
                        blocks.add(RichBlock.ofTable(td));
                    }
                    first = false;
                    continue;
                }

                StyleScope next = scope.extend(el);

                if (isBlockTag(tag) && first) {
                    for (Node inner : el.childNodes()) {
                        walk(inner, next, runs, blocks);
                    }
                    first = false;
                } else {
                    walk(el, scope, runs, blocks);
                    first = false;
                }
            }
            runs.add(RichRun.paragraphBreak());
        }
    }

    private boolean isBlockTag(String tag) {
        return switch (tag) {
            case "p", "div", "h1", "h2", "h3", "h4", "h5", "h6",
                 "li", "blockquote" -> true;
            default -> false;
        };
    }

    public RichTableData parseTable(Element table, StyleScope baseScope) {
        List<Element> trs = collectTableRows(table);
        List<RichTableCell> cells = new ArrayList<>();
        List<List<RichTableCell>> grid = new ArrayList<>();

        int rowIdx = 0;
        for (Element tr : trs) {
            ensureRow(grid, rowIdx);
            int col = nextFreeCol(grid, rowIdx);

            for (Element cellEl : tr.children()) {
                String ct = cellEl.tagName().toLowerCase();
                if (!ct.equals("td") && !ct.equals("th")) continue;

                while (isOccupied(grid, rowIdx, col)) col++;

                int colspan = parseIntOr(cellEl.attr("colspan"), 1);
                int rowspan = parseIntOr(cellEl.attr("rowspan"), 1);
                boolean isHeader = ct.equals("th");

                List<RichBlock> cellBlocks = parseNodes(new ArrayList<>(cellEl.childNodes()));

                String bg = normalizeColorOrNull(readStr(cellEl, "background-color"));

                BorderSide top    = parseBorderSide(cellEl, "top");
                BorderSide right  = parseBorderSide(cellEl, "right");
                BorderSide bottom = parseBorderSide(cellEl, "bottom");
                BorderSide left   = parseBorderSide(cellEl, "left");

                RichTableCell cell = new RichTableCell(
                        cellBlocks,
                        isHeader,
                        Math.max(1, colspan),
                        Math.max(1, rowspan),
                        rowIdx,
                        col,
                        parseRawWidths(cellEl),
                        bg,
                        null, null, null,
                        top, right, bottom, left,
                        parsePadding(cellEl),
                        resolveAlign(cellEl, isHeader),
                        resolveVerticalAlign(cellEl)
                );
                cells.add(cell);

                if (DEBUG_BORDERS) {
                    System.err.println(String.format(
                            "[BORDER] <%s> r=%d c=%d | T=%s | R=%s | B=%s | L=%s",
                            ct, rowIdx, col,
                            describeSide(top), describeSide(right),
                            describeSide(bottom), describeSide(left)
                    ));
                }

                for (int rr = 0; rr < cell.rowspan(); rr++) {
                    ensureRow(grid, rowIdx + rr);
                    for (int cc = 0; cc < cell.colspan(); cc++) {
                        setAt(grid, rowIdx + rr, col + cc, cell);
                    }
                }
                col += cell.colspan();
            }
            rowIdx++;
        }

        int colCount = 0;
        for (List<RichTableCell> row : grid) {
            if (row.size() > colCount) colCount = row.size();
        }
        if (colCount == 0) colCount = 1;

        return new RichTableData(cells, grid.size(), colCount, resolveColWidths(table, cells, colCount));
    }

    private String describeSide(BorderSide s) {
        if (s == null) return "—";
        return String.format("%.1f %s %s", s.width(), s.style(), s.color());
    }

    private List<Element> collectTableRows(Element table) {
        List<Element> out = new ArrayList<>();
        for (Element child : table.children()) {
            String tag = child.tagName().toLowerCase();
            if (tag.equals("tr")) {
                out.add(child);
            } else if (tag.equals("thead") || tag.equals("tbody") || tag.equals("tfoot")) {
                for (Element inner : child.children()) {
                    if (inner.tagName().equalsIgnoreCase("tr")) {
                        out.add(inner);
                    }
                }
            }
        }
        return out;
    }

    private void ensureRow(List<List<RichTableCell>> grid, int r) {
        while (grid.size() <= r) grid.add(new ArrayList<>());
    }

    private int nextFreeCol(List<List<RichTableCell>> grid, int r) {
        List<RichTableCell> row = grid.get(r);
        int c = 0;
        while (c < row.size() && row.get(c) != null) c++;
        return c;
    }

    private boolean isOccupied(List<List<RichTableCell>> grid, int r, int c) {
        if (r >= grid.size()) return false;
        List<RichTableCell> row = grid.get(r);
        return c < row.size() && row.get(c) != null;
    }

    private void setAt(List<List<RichTableCell>> grid, int r, int c, RichTableCell cell) {
        List<RichTableCell> row = grid.get(r);
        while (row.size() <= c) row.add(null);
        row.set(c, cell);
    }

    private static class BoxSides {
        String top, right, bottom, left;
    }

    private String normalizeCss(String css) {
        if (css == null) return null;
        Matcher m = RGB_PATTERN.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            try {
                int r = Integer.parseInt(m.group(1));
                int g = Integer.parseInt(m.group(2));
                int b = Integer.parseInt(m.group(3));
                m.appendReplacement(sb, String.format("#%02x%02x%02x", r, g, b));
            } catch (Exception e) {
                m.appendReplacement(sb, m.group());
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private BoxSides parseBox(String css) {
        if (css == null || css.isBlank()) return null;
        String[] parts = css.trim().split("\\s+");
        BoxSides b = new BoxSides();
        if (parts.length == 1) {
            b.top = b.right = b.bottom = b.left = parts[0];
        } else if (parts.length == 2) {
            b.top = b.bottom = parts[0];
            b.right = b.left = parts[1];
        } else if (parts.length == 3) {
            b.top = parts[0];
            b.right = b.left = parts[1];
            b.bottom = parts[2];
        } else if (parts.length >= 4) {
            b.top = parts[0];
            b.right = parts[1];
            b.bottom = parts[2];
            b.left = parts[3];
        }
        return b;
    }

    private String getBox(BoxSides b, String side) {
        if (b == null) return null;
        return switch (side) {
            case "top" -> b.top;
            case "right" -> b.right;
            case "bottom" -> b.bottom;
            case "left" -> b.left;
            default -> null;
        };
    }

    private BorderSide parseBorderSide(Element el, String side) {
        String wStr = readStr(el, "border-" + side + "-width");
        String sStr = readStr(el, "border-" + side + "-style");
        String cStr = readStr(el, "border-" + side + "-color");

        if (wStr != null || sStr != null || cStr != null) {
            double width = wStr != null ? parsePx(wStr) : 1.0;
            String style = sStr != null ? sStr.trim().toLowerCase() : "solid";
            String color = cStr != null ? normalizeColorOrNull(cStr) : "#000000";
            return new BorderSide(color, width, style);
        }

        String wStrAll = readStr(el, "border-width");
        String sStrAll = readStr(el, "border-style");
        String cStrAll = readStr(el, "border-color");

        if (wStrAll != null || sStrAll != null || cStrAll != null) {
            String normW = normalizeCss(wStrAll);
            String normS = normalizeCss(sStrAll);
            String normC = normalizeCss(cStrAll);

            BoxSides widths = parseBox(normW);
            BoxSides styles = parseBox(normS);
            BoxSides colors = parseBox(normC);

            String w = getBox(widths, side);
            String s = getBox(styles, side);
            String c = getBox(colors, side);

            double width = w != null ? parsePx(w) : 1.0;
            String style = s != null ? s.trim().toLowerCase() : "solid";
            String color = c != null ? normalizeColorOrNull(c) : "#000000";
            return new BorderSide(color, width, style);
        }

        String shorthand = readStr(el, "border-" + side);
        if (shorthand != null && !shorthand.isBlank()) {
            return parseBorderShorthand(normalizeCss(shorthand));
        }

        String commonShorthand = readStr(el, "border");
        if (commonShorthand != null && !commonShorthand.isBlank()) {
            return parseBorderShorthand(normalizeCss(commonShorthand));
        }

        return null;
    }

    private BorderSide parseBorderShorthand(String raw) {
        String s = raw.trim().toLowerCase();
        if (s.equals("none") || s.equals("0")) {
            return new BorderSide(null, 0, "none");
        }

        double width = 1.0;
        String style = "solid";
        String color = null;

        for (String token : s.split("\\s+")) {
            if (token.isEmpty()) continue;

            Double px = tryParsePx(token);
            if (px != null) { width = px; continue; }

            switch (token) {
                case "solid", "dashed", "dotted", "double", "groove",
                     "ridge", "inset", "outset", "none" -> style = token;
                default -> color = token;
            }
        }
        return new BorderSide(color, width, style);
    }

    private Double tryParsePx(String token) {
        String t = token.trim();
        if (t.isEmpty()) return null;
        if (t.endsWith("px")) t = t.substring(0, t.length() - 2);
        if (t.equals("0")) return 0.0;
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String resolveAlign(Element el, boolean isHeader) {
        String s = normalizeAlign(readStr(el, "text-align"));
        if (s != null) return s;

        for (Element child : el.children()) {
            String tag = child.tagName().toLowerCase();
            if (tag.equals("p") || tag.equals("div") || tag.equals("h1") || tag.equals("h2") || tag.equals("h3")) {
                String s2 = normalizeAlign(readStr(child, "text-align"));
                if (s2 != null) return s2;
                break;
            }
        }
        return "left";
    }

    private String normalizeAlign(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.trim().toLowerCase();
        if (s.equals("start")) return "left";
        if (s.equals("end"))   return "right";
        if (s.equals("left") || s.equals("center") || s.equals("right") || s.equals("justify")) {
            return s;
        }
        return null;
    }

    private String resolveVerticalAlign(Element el) {
        String s = readStr(el, "vertical-align");
        if (s == null) return "top";
        s = s.toLowerCase();
        if (s.equals("middle") || s.equals("center")) return "middle";
        if (s.equals("bottom")) return "bottom";
        return "top";
    }

    private static String normalizeColorOrNull(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim().toLowerCase();
        if (s.equals("none") || s.equals("inherit") || s.equals("initial")) {
            return null;
        }

        Matcher m = RGB_PATTERN.matcher(v);
        if (m.find()) {
            int r = Integer.parseInt(m.group(1));
            int g = Integer.parseInt(m.group(2));
            int b = Integer.parseInt(m.group(3));
            return String.format("#%02x%02x%02x", r, g, b);
        }

        return v;
    }

    private RichTableCellPadding parsePadding(Element el) {
        String shorthand = readStr(el, "padding");
        if (shorthand != null && !shorthand.isBlank()) {
            String[] parts = shorthand.trim().split("\\s+");
            double[] px = new double[parts.length];
            for (int i = 0; i < parts.length; i++) px[i] = parsePx(parts[i]);
            return switch (parts.length) {
                case 1 -> new RichTableCellPadding(px[0], px[0], px[0], px[0]);
                case 2 -> new RichTableCellPadding(px[0], px[1], px[0], px[1]);
                case 3 -> new RichTableCellPadding(px[0], px[1], px[2], px[1]);
                default -> new RichTableCellPadding(px[0], px[1], px[2], px[3]);
            };
        }
        Double top    = readDouble(el, "padding-top");
        Double right  = readDouble(el, "padding-right");
        Double bottom = readDouble(el, "padding-bottom");
        Double left   = readDouble(el, "padding-left");
        if (top == null && right == null && bottom == null && left == null) {
            return null;
        }
        return new RichTableCellPadding(
                top    != null ? top    : 0,
                right  != null ? right  : 0,
                bottom != null ? bottom : 0,
                left   != null ? left   : 0
        );
    }

    private String readStr(Element el, String key) {
        String style = el.attr("style");
        if (style == null || style.isBlank()) return null;
        for (String part : style.split(";")) {
            int colon = part.indexOf(':');
            if (colon <= 0) continue;
            String k = part.substring(0, colon)
                    .replace('\u00A0', ' ')
                    .trim()
                    .toLowerCase();
            if (k.equals(key)) {
                return part.substring(colon + 1)
                        .replace('\u00A0', ' ')
                        .trim();
            }
        }
        return null;
    }

    private Double readDouble(Element el, String key) {
        String v = readStr(el, key);
        if (v == null) return null;
        try {
            return parsePx(v);
        } catch (Exception e) {
            return null;
        }
    }

    private double parsePx(String v) {
        if (v == null) return 0;
        String s = v.replace("px", "").trim();
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int parseIntOr(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private double[] resolveColWidths(Element table, List<RichTableCell> cells, int colCount) {
        double[] fromCells = readCellWidths(cells, colCount);
        if (fromCells != null) return fromCells;
        double[] fromColgroup = readColgroup(table, colCount);
        if (fromColgroup != null) return fromColgroup;
        return null;
    }

    private double[] readColgroup(Element table, int colCount) {
        Element colgroup = null;
        for (Element child : table.children()) {
            if (child.tagName().equalsIgnoreCase("colgroup")) {
                colgroup = child;
                break;
            }
        }
        if (colgroup == null) return null;

        List<Element> cols = new ArrayList<>();
        for (Element c : colgroup.children()) {
            if (c.tagName().equalsIgnoreCase("col")) cols.add(c);
        }
        if (cols.size() != colCount) return null;

        double[] widths = new double[colCount];
        boolean any = false;

        for (int i = 0; i < colCount; i++) {
            Double w = readDouble(cols.get(i), "width");
            if (w == null || w <= 0) {
                String aw = cols.get(i).attr("width");
                if (aw != null && !aw.isBlank()) {
                    try {
                        double v = Double.parseDouble(aw.trim());
                        if (v > 0) w = v;
                    } catch (Exception ignored) {}
                }
            }
            if (w != null && w > 0) {
                widths[i] = w;
                any = true;
            }
        }
        return any ? widths : null;
    }

    private double[] readCellWidths(List<RichTableCell> cells, int colCount) {
        double[] widths = new double[colCount];
        boolean any = false;

        for (RichTableCell c : cells) {
            if (c.rawWidths() == null || c.rawWidths().length == 0) continue;

            if (c.rawWidths().length == c.colspan()) {
                for (int i = 0; i < c.colspan(); i++) {
                    int col = c.colIndex() + i;
                    if (col < colCount && widths[col] == 0) {
                        widths[col] = c.rawWidths()[i];
                        any = true;
                    }
                }
            } else if (c.rawWidths().length == 1) {
                if (widths[c.colIndex()] == 0) {
                    widths[c.colIndex()] = c.rawWidths()[0];
                    any = true;
                }
            }
        }
        return any ? widths : null;
    }

    private double[] parseRawWidths(Element cellEl) {
        String attr = cellEl.attr("colwidth");
        if (attr != null && !attr.isBlank() && !"null".equals(attr)) {
            String[] parts = attr.split(",");
            double[] arr = new double[parts.length];
            int n = 0;
            for (String p : parts) {
                try {
                    double v = Double.parseDouble(p.trim());
                    if (v > 0) arr[n++] = v;
                } catch (NumberFormatException ignored) {}
            }
            if (n > 0) {
                double[] out = new double[n];
                System.arraycopy(arr, 0, out, 0, n);
                return out;
            }
        }

        Double w = readDouble(cellEl, "width");
        if (w != null && w > 0) return new double[]{w};

        return null;
    }

    private void flushRuns(List<RichBlock> blocks, List<RichRun> runs) {
        List<RichRun> normalized = normalize(runs);
        if (!normalized.isEmpty()) {
            blocks.add(RichBlock.ofText(normalized));
        }
        runs.clear();
    }

    private List<RichRun> normalize(List<RichRun> runs) {
        List<RichRun> out = new ArrayList<>(runs);
        while (!out.isEmpty() && out.get(out.size() - 1).isBreak()) {
            out.remove(out.size() - 1);
        }
        while (!out.isEmpty() && out.get(0).isBreak()) {
            out.remove(0);
        }
        return out;
    }
}