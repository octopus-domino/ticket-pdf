package ru.ticket.recognition;

import java.io.IOException;
import java.util.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

/** Adapter: existing PDF glyphs are authoritative; scanned pages return no words and use OCR. */
@Component
public class PdfTextExtractor {
  /** Extracts only the selected page, in the same top-left pixel coordinates as the preview. */
  public List<OcrEngine.Word> extract(PDDocument document, int page, double scale) throws IOException {
    var reader = new Reader(scale);
    reader.setStartPage(page);
    reader.setEndPage(page);
    reader.setSortByPosition(true);
    reader.getText(document);
    return List.copyOf(reader.words);
  }

  private static final class Reader extends PDFTextStripper {
    private final double scale;
    private final List<OcrEngine.Word> words = new ArrayList<>();

    private Reader(double scale) throws IOException { this.scale = scale; }

    @Override
    protected void writeString(String text, List<TextPosition> positions) {
      var run = new ArrayList<TextPosition>();
      for (var position : positions) {
        if (position.getUnicode().isBlank()) { flush(run); continue; }
        if (!run.isEmpty() && separate(run.get(run.size() - 1), position)) flush(run);
        run.add(position);
      }
      flush(run);
    }

    private boolean separate(TextPosition previous, TextPosition next) {
      double gap = next.getXDirAdj() - previous.getXDirAdj() - previous.getWidthDirAdj();
      return gap > Math.max(1, previous.getWidthOfSpace() * .6)
          || !previous.getFont().getName().equals(next.getFont().getName())
          || Math.abs(previous.getFontSizeInPt() - next.getFontSizeInPt()) > .5;
    }

    private void flush(List<TextPosition> run) {
      if (run.isEmpty()) return;
      var first = run.get(0);
      double left = run.stream().mapToDouble(TextPosition::getXDirAdj).min().orElse(0);
      double top = run.stream().mapToDouble(p -> p.getYDirAdj() - p.getFontSizeInPt() * .8).min().orElse(0);
      double right = run.stream().mapToDouble(p -> p.getXDirAdj() + p.getWidthDirAdj()).max().orElse(left);
      double bottom = run.stream().mapToDouble(p -> p.getYDirAdj() + p.getFontSizeInPt() * .2).max().orElse(top);
      String text = String.join("", run.stream().map(TextPosition::getUnicode).toList());
      String font = first.getFont().getName().toLowerCase(Locale.ROOT);
      var hints = new OcrEngine.StyleHints(family(font), first.getFontSizeInPt() * scale,
          font.contains("bold"), font.contains("italic") || font.contains("oblique"));
      if (right > left && bottom > top && top >= 0)
        words.add(new OcrEngine.Word(text, (int) Math.floor(left * scale), (int) Math.floor(top * scale),
            Math.max(1, (int) Math.ceil((right - left) * scale)), Math.max(1, (int) Math.ceil((bottom - top) * scale)),
            100, "pdf:" + Math.round(first.getYDirAdj() * scale / 2), hints));
      run.clear();
    }

    private String family(String font) {
      if (font.contains("times")) return "Times New Roman";
      if (font.contains("courier")) return "Courier New";
      return "Arial";
    }
  }
}
