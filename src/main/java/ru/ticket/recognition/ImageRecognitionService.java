package ru.ticket.recognition;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;

@Service
public class ImageRecognitionService {
  private final OcrEngine ocr;
  private final PageLayoutAnalyzer analyzer;
  private final LocalVisionRecognizer vision;
  private final Semaphore capacity = new Semaphore(1);
  public ImageRecognitionService(OcrEngine ocr, PageLayoutAnalyzer analyzer, LocalVisionRecognizer vision) {
    this.ocr = ocr; this.analyzer = analyzer; this.vision = vision;
  }
  public RecognitionDraft recognize(String data, String language, String engine) {
    if (!Set.of("eng", "rus+eng").contains(language) || !Set.of("ocr", "ollama").contains(engine)) throw ApiException.badRequest("recognition.invalid_request");
    if (!capacity.tryAcquire()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "recognition.busy");
    try {
      BufferedImage image = decode(data);
      return engine.equals("ollama") ? vision.recognize(image) : analyzer.analyze(image, ocr.recognize(image, language));
    } finally { capacity.release(); }
  }
  static BufferedImage decode(String data) {
    if (data == null || data.length() > 11_200_000 || !(data.startsWith("data:image/png;base64,") || data.startsWith("data:image/jpeg;base64,")))
      throw ApiException.badRequest("recognition.invalid_image");
    try {
      byte[] bytes = Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1));
      if (bytes.length > 8 * 1024 * 1024) throw ApiException.badRequest("recognition.invalid_image");
      try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = ImageIO.getImageReaders(stream);
        if (!readers.hasNext()) throw ApiException.badRequest("recognition.invalid_image");
        var reader = readers.next();
        try {
          reader.setInput(stream, true, true);
          int w = reader.getWidth(0), h = reader.getHeight(0);
          if (w < 32 || h < 32 || w > 4096 || h > 4096 || (long)w * h > 6_000_000) throw ApiException.badRequest("recognition.invalid_image");
          var raw = reader.read(0);
          // Transparent pixels must not turn into black OCR noise.
          var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
          var graphics = image.createGraphics();
          try { graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, w, h); graphics.drawImage(raw, 0, 0, null); }
          finally { graphics.dispose(); raw.flush(); }
          return image;
        } finally { reader.dispose(); }
      }
    } catch (IllegalArgumentException | IOException error) { throw ApiException.badRequest("recognition.invalid_image"); }
  }
}
