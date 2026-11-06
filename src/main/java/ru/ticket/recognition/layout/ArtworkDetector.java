package ru.ticket.recognition.layout;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.ArtworkBackground;
import ru.ticket.recognition.ImageEncoding;
import ru.ticket.recognition.RecognitionDraft.Item;

/**
 * Тайловая эвристика «здесь что-то нарисовано» — fallback, когда точный
 * {@code ShapeRecognizer} недоступен.
 *
 * <p><b>Почему тайлы.</b> Пиксельная сегментация на 4096² была бы слишком
 * медленной; сетка 12×12 в 34000 раз меньше по числу итераций и достаточно
 * точна для блочной графики.
 */
@Component
public class ArtworkDetector {

    private static final int TILE_SIZE = 12;
    private static final int MIN_TILES_PER_OBJECT = 5;
    private static final int MIN_SIDE_PX = 24;
    private static final int MAX_CROPS = 32;

    public List<Item> detect(BufferedImage image, boolean[] excluded) {
        int width = image.getWidth();
        int height = image.getHeight();
        int cols = (width + TILE_SIZE - 1) / TILE_SIZE;
        int rows = (height + TILE_SIZE - 1) / TILE_SIZE;

        boolean[] ink = new boolean[cols * rows];
        boolean[] seen = new boolean[cols * rows];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (excluded[y * width + x]) continue;
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 255;
                int g = (rgb >> 8) & 255;
                int b = rgb & 255;
                if (r < 220 || g < 220 || b < 220) {
                    ink[(y / TILE_SIZE) * cols + x / TILE_SIZE] = true;
                }
            }
        }

        var result = new ArrayList<Item>();
        int crops = 0;

        for (int i = 0; i < ink.length; i++) {
            if (!ink[i] || seen[i]) continue;

            var queue = new ArrayDeque<Integer>();
            queue.add(i);
            seen[i] = true;

            int minCol = cols, minRow = rows, maxCol = 0, maxRow = 0, count = 0;
            while (!queue.isEmpty()) {
                int cell = queue.removeFirst();
                int x = cell % cols, y = cell / cols;
                count++;
                minCol = Math.min(minCol, x);
                minRow = Math.min(minRow, y);
                maxCol = Math.max(maxCol, x);
                maxRow = Math.max(maxRow, y);

                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= cols || ny < 0 || ny >= rows) continue;
                        int next = ny * cols + nx;
                        if (!seen[next] && ink[next]) {
                            seen[next] = true;
                            queue.add(next);
                        }
                    }
                }
            }

            int x = minCol * TILE_SIZE;
            int y = minRow * TILE_SIZE;
            int w = Math.min(width, (maxCol + 1) * TILE_SIZE) - x;
            int h = Math.min(height, (maxRow + 1) * TILE_SIZE) - y;

            if (count < MIN_TILES_PER_OBJECT
                    || w < MIN_SIDE_PX || h < MIN_SIDE_PX
                    || ++crops > MAX_CROPS) {
                continue;
            }

            var crop = ArtworkBackground.restore(image, excluded, x, y, w, h);

            if (isUniform(crop)) {
                int color = crop.getRGB(0, 0);
                result.add(new Item("shape", x, y, w, h,
                        Map.of("shape", "rect",
                                "fill", String.format(Locale.ROOT, "#%06x", color & 0xffffff))));
            } else {
                result.add(new Item("image", x, y, w, h,
                        Map.of("src", ImageEncoding.pngDataUrl(crop))));
            }
        }
        return result;
    }

    private static boolean isUniform(BufferedImage image) {
        int first = image.getRGB(0, 0);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != first) return false;
            }
        }
        return true;
    }
}