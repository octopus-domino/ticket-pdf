package ru.ticket.profile;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import ru.ticket.common.ApiException;

final class ProfileImageValidator {
  private static final int MAX_DIMENSION = 1024;
  private static final String EMBEDDED_IMAGE =
      "^data:image/(png|jpeg);base64,[A-Za-z0-9+/=]+$";

  private ProfileImageValidator() {}

  static void validate(String avatar) {
    if (avatar.isEmpty()) return;
    if (!avatar.matches(EMBEDDED_IMAGE)) {
      throw ApiException.badRequest("Profile image must be an embedded PNG or JPEG");
    }

    // Read dimensions from metadata without decoding the full pixel buffer.
    try {
      byte[] bytes = Base64.getDecoder().decode(avatar.substring(avatar.indexOf(',') + 1));
      try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw new IllegalArgumentException();
        var reader = readers.next();
        try {
          reader.setInput(input);
          int width = reader.getWidth(0);
          int height = reader.getHeight(0);
          if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new IllegalArgumentException();
          }
        } finally {
          reader.dispose();
        }
      }
    } catch (Exception error) {
      throw ApiException.badRequest("Profile image is invalid or exceeds 1024 pixels");
    }
  }
}
