package ru.ticket.recognition.layout;

import java.awt.image.BufferedImage;

/** Restores only verifiable plain/gradient backgrounds; complex artwork stays transparent under editable text. */
final class UniformTextBackground {
    private static final int SEARCH_LIMIT = 128;
    private static final int COLOR_TOLERANCE = 48;

    private UniformTextBackground() {}

    static void restoreRow(BufferedImage source, boolean[] mask, BufferedImage crop, int left, int top, int y) {
        int x = 0;
        while (x < crop.getWidth()) {
            if (!mask[(top + y) * source.getWidth() + left + x]) { x++; continue; }
            int start = x;
            while (x < crop.getWidth() && mask[(top + y) * source.getWidth() + left + x]) x++;
            fillRun(source, mask, crop, left, top, y, start, x);
        }
    }

    private static void fillRun(BufferedImage source, boolean[] mask, BufferedImage crop,
        int left, int top, int y, int start, int end) {
        int x1 = left + start - 1, x2 = left + end, sy = top + y;
        if (x1 < 0 || x2 >= source.getWidth()) return;
        int first = source.getRGB(x1, sy), last = source.getRGB(x2, sy);
        if (!similar(first, last)) return;
        for (int part = 1; part <= 3; part++) {
            int sx = left + start + (end - start) * part / 4;
            int expected = mix(first, last, part / 4.0);
            if (!verified(source, mask, sx, sy, expected, -1) || !verified(source, mask, sx, sy, expected, 1)) return;
        }
        for (int x = start; x < end; x++) crop.setRGB(x, y, mix(first, last, (x - start + 1.0) / (end - start + 1)));
    }

    private static boolean verified(BufferedImage source, boolean[] mask, int x, int y, int expected, int direction) {
        for (int distance = 1; distance <= SEARCH_LIMIT; distance++) {
            int row = y + distance * direction;
            if (row < 0 || row >= source.getHeight()) return false;
            if (!mask[row * source.getWidth() + x]) return similar(source.getRGB(x, row), expected);
        }
        return false;
    }

    private static boolean similar(int first, int second) {
        for (int shift : new int[]{0, 8, 16})
            if (Math.abs((first >> shift & 255) - (second >> shift & 255)) > COLOR_TOLERANCE) return false;
        return true;
    }

    private static int mix(int first, int second, double weight) {
        int result = 0xff000000;
        for (int shift : new int[]{0, 8, 16}) result |= (int) Math.round((first >> shift & 255) * (1 - weight)
            + (second >> shift & 255) * weight) << shift;
        return result;
    }
}
