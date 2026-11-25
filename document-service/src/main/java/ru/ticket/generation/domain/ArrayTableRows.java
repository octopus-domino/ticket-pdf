package ru.ticket.generation.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import ru.ticket.dto.TableElement;

final class ArrayTableRows {
  private static final Pattern INDEXED_CELL =
      Pattern.compile("^\\{\\{\\s*([\\w.]+)\\[(\\d+)\\]([\\w.\\[\\]]*)\\s*\\}\\}$");

  private ArrayTableRows() {}

  static String path(TableElement table) {
    if (table.repeatPath != null && !table.repeatPath.isBlank()) return table.repeatPath;
    if (table.data == null || table.data.size() != table.rows || table.rows < 2 || table.cols < 1) return null;
    var header = table.data.get(0);
    var first = table.data.get(1);
    if (header == null || header.size() != table.cols || first == null || first.size() != table.cols
        || header.stream().anyMatch(cell -> cell == null || cell.contains("{{"))) return null;

    var lead = INDEXED_CELL.matcher(first.get(0) == null ? "" : first.get(0));
    if (!lead.matches()) return null;
    String path = lead.group(1);
    var fields = new ArrayList<String>();
    for (int row = 1; row < table.rows; row++) {
      var cells = table.data.get(row);
      if (cells == null || cells.size() != table.cols) return null;
      for (int column = 0; column < table.cols; column++) {
        String cell = cells.get(column);
        var match = INDEXED_CELL.matcher(cell == null ? "" : cell);
        if (!match.matches() || !path.equals(match.group(1)) || !Integer.toString(row - 1).equals(match.group(2))) return null;
        if (row == 1) fields.add(match.group(3));
        else if (!fields.get(column).equals(match.group(3))) return null;
      }
    }
    return path;
  }

  static boolean fill(TableElement table, String path, Object value, Map<String, String> fields) {
    if (!(value instanceof List<?> items)) return false;

    var data = new ArrayList<List<String>>();
    var heights = new ArrayList<Double>();
    var styles = table.cellStyles == null ? null : new ArrayList<List<TableElement.CellStyle>>();
    double[] templateHeights = rowHeights(table);
    data.add(cellsAt(table, 0).stream().map(cell -> DataFiller.replacePlaceholders(cell, fields)).toList());
    heights.add(templateHeights[0]);
    if (styles != null) styles.add(styleAt(table, 0));
    for (int index = 0; index < items.size(); index++) {
      int templateRow = index + 1 < table.rows && hasRow(table, index + 1) ? index + 1 : 1;
      String prefix = path + "[" + (templateRow - 1) + "]";
      Object item = items.get(index);
      data.add(cellsAt(table, templateRow).stream()
          .map(cell -> DataFiller.replacePlaceholders(cell, field -> readField(field, prefix, item, fields)))
          .toList());
      heights.add(templateHeights[Math.min(templateRow, templateHeights.length - 1)]);
      if (styles != null) styles.add(styleAt(table, templateRow));
    }
    table.data = data;
    table.rows = data.size();
    table.rowHeights = heights;
    table.height = heights.stream().mapToDouble(Double::doubleValue).sum();
    table.cellStyles = styles;
    table.repeatPath = null;
    return true;
  }

  private static boolean hasRow(TableElement table, int row) {
    return table.data != null && row < table.data.size() && table.data.get(row) != null;
  }

  private static List<String> cellsAt(TableElement table, int row) {
    return hasRow(table, row) ? table.data.get(row) : List.of();
  }

  private static List<TableElement.CellStyle> styleAt(TableElement table, int row) {
    var styles = row < table.cellStyles.size() ? table.cellStyles.get(row) : null;
    return styles == null ? List.of() : styles;
  }

  private static double[] rowHeights(TableElement table) {
    int count = Math.max(1, table.rows);
    var weights = table.rowHeights;
    boolean valid = weights != null && weights.size() == count
        && weights.stream().allMatch(weight -> weight != null && Double.isFinite(weight) && weight > 0);
    double sum = valid ? weights.stream().mapToDouble(Double::doubleValue).sum() : count;
    double[] heights = new double[count];
    for (int row = 0; row < count; row++) heights[row] = table.height * (valid ? weights.get(row) : 1) / sum;
    return heights;
  }

  private static String readField(String field, String prefix, Object item, Map<String, String> fields) {
    Object value;
    if (field.equals(prefix)) value = item;
    else if (field.startsWith(prefix + ".") || field.startsWith(prefix + "[")) {
      // A single root also supports arrays of arrays and keeps normal path validation.
      value = item == null ? null : ModelValues.get(Map.of("row", item), "row" + field.substring(prefix.length()));
    } else return fields.get(field);
    return value == null ? null : String.valueOf(value);
  }
}
