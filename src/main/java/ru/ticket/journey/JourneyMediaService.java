package ru.ticket.journey;

import java.awt.image.BufferedImage;
import java.io.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;

/** Bounded, deduplicated image storage. Document data contains references, never base64 copies. */
@Service
public class JourneyMediaService {
  public record Asset(String id, int width, int height) {}

  private final JourneyMediaRepository repository;
  private final int maxItems;

  public JourneyMediaService(
      JourneyMediaRepository repository,
      @Value("${app.journeys.media.max-items:512}") int maxItems) {
    this.repository = repository;
    this.maxItems = maxItems;
  }

  @Transactional
  public Asset save(String data) {
    return saveImage(data, false);
  }
  @Transactional
  public Asset saveBackground(String data) { return saveImage(data, true); }
  private Asset saveImage(String data, boolean background) {
    int maxBytes = background ? 6_000_000 : 900_000;
    if (data == null
        || data.length() > (background ? 8_000_100 : 1200000)
        || !(data.startsWith("data:image/png;base64,")
            || data.startsWith("data:image/jpeg;base64,")))
      throw ApiException.badRequest("journey.media.invalid");
    BufferedImage decoded = null, clean = null;
    try {
      byte[] input = Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1));
      if (input.length > maxBytes) throw ApiException.badRequest("journey.media.invalid");
      try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(input))) {
        var readers = ImageIO.getImageReaders(stream);
        if (!readers.hasNext()) throw ApiException.badRequest("journey.media.invalid");
        var reader = readers.next();
        try {
          reader.setInput(stream, true, true);
          String format = reader.getFormatName().toLowerCase(Locale.ROOT);
          int width = reader.getWidth(0), height = reader.getHeight(0);
          if (!Set.of("png", "jpeg", "jpg").contains(format)
              || width < 1
              || height < 1
              || width > 2400
              || height > 2400
              || (long) width * height > 4000000)
            throw ApiException.badRequest("journey.media.invalid");
          decoded = reader.read(0);
        } finally {
          reader.dispose();
        }
      }
      boolean alpha = background || decoded.getColorModel().hasAlpha();
      clean =
          new BufferedImage(
              decoded.getWidth(),
              decoded.getHeight(),
              alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
      var graphics = clean.createGraphics();
      try {
        graphics.drawImage(decoded, 0, 0, null);
      } finally {
        graphics.dispose();
      }
      var output = new ByteArrayOutputStream();
      ImageIO.write(clean, alpha ? "png" : "jpeg", output);
      byte[] bytes = output.toByteArray();
      if (bytes.length > maxBytes) throw ApiException.badRequest("journey.media.invalid");
      String id = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      if (!repository.existsById(id)) {
        if (repository.count() >= maxItems) throw ApiException.conflict("journey.media.full");
        var entity = new JourneyMedia();
        entity.id = id;
        entity.content = bytes;
        entity.contentType = alpha ? "image/png" : "image/jpeg";
        entity.width = clean.getWidth();
        entity.height = clean.getHeight();
        entity.createdAt = Instant.now();
        repository.save(entity);
      }
      return new Asset(id, clean.getWidth(), clean.getHeight());
    } catch (IOException
        | IllegalArgumentException
        | java.security.NoSuchAlgorithmException error) {
      throw ApiException.badRequest("journey.media.invalid");
    } finally {
      if (decoded != null) decoded.flush();
      if (clean != null) clean.flush();
    }
  }

  @Transactional(readOnly = true)
  public JourneyMedia get(String id) {
    if (id == null || !id.matches("[a-f0-9]{64}")) throw ApiException.notFound("Image");
    return repository.findById(id).orElseThrow(() -> ApiException.notFound("Image"));
  }
}
