package ru.ticket.generation.application.port;

import java.util.List;
import ru.ticket.dto.Slide;
import ru.ticket.export.ExportFormat;
import ru.ticket.generation.application.dto.NamedDocument;

public interface DocumentRenderer {

  ExportFormat format();

  byte[] render(Slide slide);

  /**
   * Многостраничный рендер. Возвращает список NamedDocument: - PDF/PDF-A — ровно 1 элемент
   * (многостраничный файл); - SVG — по одному элементу на слайд.
   *
   * @param slides заполненные слайды (1+)
   * @param filenameBase база имени без расширения
   */
  default List<NamedDocument> renderMulti(List<Slide> slides, String filenameBase) {
    throw new UnsupportedOperationException("Multi-slide render not supported for " + format());
  }

  default List<NamedDocument> renderMulti(
      List<Slide> slides, String base, ru.ticket.dto.ExportOptions options) {
    return renderMulti(slides, base);
  }

  String mimeType();

  String extension();
}
