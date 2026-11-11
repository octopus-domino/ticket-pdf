package ru.ticket.recognition;

import java.awt.image.BufferedImage;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;

/**
 * API-facing фасад: валидация, семафор, роутинг движков.
 *
 * <ul>
 *   <li>{@code ocr} → {@link CompositeRecognitionService} (dual Tesseract + shapes).</li>
 *   <li>{@code ollama} → {@link LocalVisionRecognizer} (мультимодальная модель).</li>
 * </ul>
 */
@Service
public class ImageRecognitionService {

  private final ImageDecoder decoder;
  private final CompositeRecognitionService composite;
  private final LocalVisionRecognizer vision;

  // Один одновременный OCR-проход: tesseract сам по себе CPU-bound.
  private final ru.ticket.common.HeavyOperationCapacity capacity;

  public ImageRecognitionService(
          ImageDecoder decoder,
          CompositeRecognitionService composite,
          LocalVisionRecognizer vision,ru.ticket.common.HeavyOperationCapacity capacity) {
    this.decoder = decoder;
    this.composite = composite;
    this.vision = vision;
    this.capacity = capacity;
  }

  public RecognitionDraft recognize(String data, String language, String engine) {
    if (!Set.of("eng", "rus+eng").contains(language)
            || !Set.of("ocr", "ollama").contains(engine)) {
      throw ApiException.badRequest("recognition.invalid_request");
    }
    if (!capacity.tryAcquire()) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "recognition.busy");
    }
    BufferedImage image = null;
    try {
      image = decoder.decode(data);
      return "ollama".equals(engine)
              ? vision.recognize(image)
              : composite.recognize(image, language);
    } finally {
      if (image != null) image.flush();
      capacity.release();
    }
  }
}
