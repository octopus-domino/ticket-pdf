package ru.ticket.recognition;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;

/**
 * Кодирование растра в data-URL для встраивания в SVG/JSON.
 *
 * <p><b>SRP.</b> Единственная задача: пиксели → base64-PNG data URL.
 * Используется и {@code ArtworkDetector} (возврат кропа логотипа), и
 * {@code LocalVisionRecognizer} (отправка картинки в Ollama и возврат
 * crop-изображений из vision-модели).
 *
 * <p>Вынесен из {@code PageLayoutAnalyzer} после рефакторинга: иначе
 * получалась странная зависимость «распознавание через Ollama знает
 * про PageLayoutAnalyzer, чтобы закодировать PNG».
 */
public final class ImageEncoding {

    private ImageEncoding() {
    }

    /**
     * Кодирует изображение в data-URL {@code data:image/png;base64,...}.
     *
     * @throws IllegalStateException если ImageIO не смог сериализовать
     *                               изображение (не должно случаться на
     *                               стандартных BufferedImage)
     */
    public static String pngDataUrl(BufferedImage image) {
        try {
            var output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException error) {
            throw new IllegalStateException("Image encoding failed", error);
        }
    }
}