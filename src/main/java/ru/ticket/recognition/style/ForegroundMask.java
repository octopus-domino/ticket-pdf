package ru.ticket.recognition.style;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Separates glyphs from their local paper color, regardless of text polarity. */
final class ForegroundMask {
    private ForegroundMask() {}

    static BufferedImage normalize(BufferedImage source, int x, int y, int width, int height) {
        var border = new HashMap<Integer, Integer>();
        for (int row = Math.max(0, y - 3); row < Math.min(source.getHeight(), y + height + 3); row++) {
            for (int col = Math.max(0, x - 3); col < Math.min(source.getWidth(), x + width + 3); col++) {
                if (col < x || col >= x + width || row < y || row >= y + height)
                    border.merge(source.getRGB(col, row) & 0xffffff, 1, Integer::sum);
            }
        }
        int background = border.entrySet().stream()
                .max(Map.Entry.<Integer, Integer>comparingByValue().thenComparingInt(Map.Entry::getKey))
                .map(Map.Entry::getKey).orElse(0xffffff);
        var result = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
        int peak = 0;
        for (int row = 0; row < height; row++) for (int col = 0; col < width; col++) {
            if (inside(source, x + col, y + row))
                peak = Math.max(peak, distance(source.getRGB(x + col, y + row), background));
        }
        double threshold = Math.max(900, peak * .25);
        for (int row = 0; row < result.getHeight(); row++) for (int col = 0; col < result.getWidth(); col++) {
            boolean ink = inside(source, x + col, y + row)
                    && distance(source.getRGB(x + col, y + row), background) > threshold;
            result.setRGB(col, row, ink ? 0 : 0xffffff);
        }
        return result;
    }

    private static boolean inside(BufferedImage image, int x, int y) {
        return x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight();
    }

    private static int distance(int a, int b) {
        int red = (a >> 16 & 255) - (b >> 16 & 255);
        int green = (a >> 8 & 255) - (b >> 8 & 255);
        int blue = (a & 255) - (b & 255);
        return red * red + green * green + blue * blue;
    }
}
