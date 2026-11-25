package ru.ticket.recognition;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;

/**
 * Декодирует входящий data-URL в {@link BufferedImage}, валидируя
 * ограничения безопасности и памяти.
 *
 * <p><b>SRP.</b> Только декодирование и валидация. Роутинг движков — в
 * {@link ImageRecognitionService}.
 *
 * <p><b>Почему не {@code ImageIO.read} напрямую.</b> {@code ImageIO.read}
 * полностью материализует буфер в память: 4096×4096 RGBA — это ~64 МБ.
 * Читаем заголовок через {@code ImageReader}, проверяем размеры до
 * {@code reader.read(0)} — «картинка-бомба» отклоняется без аллокации
 * пикселей.
 *
 * <p><b>Белый фон.</b> Прозрачные PNG без белой подложки дают при OCR
 * чёрные артефакты (Tesseract читает alpha=0 как «чернила»).
 */
@Component
public class ImageDecoder {

    private static final int MAX_DATA_URL_LENGTH = 11_200_000;
    private static final int MAX_RAW_BYTES = 8 * 1024 * 1024;
    private static final int MAX_SIDE = 4096;
    private static final long MAX_AREA = 6_000_000L;

    public BufferedImage decode(String data) {
        if (data == null || data.length() > MAX_DATA_URL_LENGTH
                || !(data.startsWith("data:image/png;base64,")
                || data.startsWith("data:image/jpeg;base64,"))) {
            throw ApiException.badRequest("recognition.invalid_image");
        }

        try {
            byte[] bytes = Base64.getDecoder()
                    .decode(data.substring(data.indexOf(',') + 1));
            if (bytes.length > MAX_RAW_BYTES) {
                throw ApiException.badRequest("recognition.invalid_image");
            }

            try (var stream = new MemoryCacheImageInputStream(
                    new ByteArrayInputStream(bytes))) {

                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) {
                    throw ApiException.badRequest("recognition.invalid_image");
                }

                var reader = readers.next();
                try {
                    reader.setInput(stream, true, true);
                    int w = reader.getWidth(0);
                    int h = reader.getHeight(0);
                    if (w < 32 || h < 32 || w > MAX_SIDE || h > MAX_SIDE
                            || (long) w * h > MAX_AREA) {
                        throw ApiException.badRequest("recognition.invalid_image");
                    }

                    var raw = reader.read(0);
                    var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, w, h);
                        graphics.drawImage(raw, 0, 0, null);
                    } finally {
                        graphics.dispose();
                        raw.flush();
                    }
                    return image;
                } finally {
                    reader.dispose();
                }
            }
        } catch (IllegalArgumentException | IOException error) {
            throw ApiException.badRequest("recognition.invalid_image");
        }
    }
}