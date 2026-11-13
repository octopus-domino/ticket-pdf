package ru.ticket.generation.domain;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import ru.ticket.dto.Slide;
import ru.ticket.generation.application.port.GenerationCatalog;

/** The single constructor/model substitution step, shared by workflows, previews and ready services. */
public final class TemplateMaterializer {
  private TemplateMaterializer() {}
  public static List<Slide> fill(GenerationCatalog.Template template, Map<String, Object> input) {
    var values = ModelValues.validate(input, template.fields());
    List<Slide> result = new ArrayList<>();
    for (var slide : template.slides()) {
      if (slide.repeat == null) {
        add(result, DataFiller.fill(slide, values, template.bindings())); continue;
      }
      String path = slide.repeat.path(); int size = slide.repeat.pageSize();
      ModelValues.validatePath(path);
      if (!path.matches("[A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)*") || size < 1 || size > 100)
        throw new IllegalArgumentException("Invalid page repetition settings");
      var value = ModelValues.get(values, path);
      if (!(value instanceof List<?> rows)) throw new IllegalArgumentException("Page repetition requires an array: " + path);
      for (int start = 0; start < Math.max(1, rows.size()); start += size) {
        var chunk = rows.subList(Math.min(start, rows.size()), Math.min(start + size, rows.size()));
        Map<String, Object> local;
        if (values.containsKey(path)) { local = new LinkedHashMap<>(values); local.put(path, chunk); }
        else local = replace(values, path.split("\\."), 0, chunk);
        var filled = DataFiller.fill(slide, local, template.bindings());
        filled.repeat = null; filled.id = slide.id + "-" + (start / size + 1);
        add(result, filled);
      }
    }
    return List.copyOf(result);
  }
  private static void add(List<Slide> result, Slide slide) {
    if (result.size() >= ru.ticket.dto.ExportRequest.MAX_SLIDES) throw new IllegalArgumentException("Document exceeds 500 pages");
    result.add(slide);
  }
  private static Map<String,Object> replace(Map<String,Object> source, String[] path, int index, Object value) {
    var copy = new LinkedHashMap<>(source);
    if (index == path.length - 1) copy.put(path[index], value);
    else {
      Object child = source.get(path[index]);
      if (!(child instanceof Map<?,?> map)) throw new IllegalArgumentException("Invalid repeated array path");
      Map<String,Object> typed = new LinkedHashMap<>(); map.forEach((key, item) -> typed.put(String.valueOf(key), item));
      copy.put(path[index], replace(typed, path, index + 1, value));
    }
    return copy;
  }
}
