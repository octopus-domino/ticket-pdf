package ru.ticket.recognition.style;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Чистые функции анализа растра: толщина штриха, x-height,
 * подчёркивание, зачёркивание, наклон. Без зависимостей от OCR.
 */
final class StrokeAnalyzer {

    private StrokeAnalyzer() {
    }

    static double medianHorizontalRunLength(
            BufferedImage img, int x, int y, int w, int h) {

        List<Integer> runs = new ArrayList<>();
        for (int row = 0; row < h; row++) {
            int run = 0;
            for (int col = 0; col < w; col++) {
                if (isDark(img.getRGB(x + col, y + row))) {
                    run++;
                } else if (run > 0) {
                    runs.add(run);
                    run = 0;
                }
            }
            if (run > 0) runs.add(run);
        }
        if (runs.isEmpty()) return 0;
        Collections.sort(runs);
        return runs.get(runs.size() / 2);
    }

    static double xHeight(BufferedImage img, int x, int y, int w, int h) {
        int[] rowDark = new int[h];
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                if (isDark(img.getRGB(x + col, y + row))) rowDark[row]++;
            }
        }
        int peak = 0;
        for (int c : rowDark) peak = Math.max(peak, c);
        if (peak == 0) return h;

        int threshold = Math.max(1, peak / 3);
        int first = -1, last = -1;
        for (int i = 0; i < h; i++) {
            if (rowDark[i] >= threshold) {
                if (first < 0) first = i;
                last = i;
            }
        }
        return first < 0 ? h : (last - first + 1);
    }

    static boolean hasUnderline(BufferedImage img, int x, int y, int w, int h) {
        int top = y + (int) (h * 0.82);
        int bottom = y + (int) (h * 0.95);
        return hasHorizontalRun(img, x, w, top, bottom, 0.6);
    }

    static boolean hasStrikethrough(BufferedImage img, int x, int y, int w, int h) {
        int top = y + (int) (h * 0.40);
        int bottom = y + (int) (h * 0.55);
        return hasHorizontalRun(img, x, w, top, bottom, 0.7);
    }

    private static boolean hasHorizontalRun(
            BufferedImage img, int x, int w, int top, int bottom, double minRatio) {

        for (int row = top; row < bottom; row++) {
            int run = 0, max = 0;
            for (int col = 0; col < w; col++) {
                if (isDark(img.getRGB(x + col, row))) {
                    run++;
                    max = Math.max(max, run);
                } else {
                    run = 0;
                }
            }
            if (max > w * minRatio) return true;
        }
        return false;
    }

    static double italicSkew(BufferedImage img, int x, int y, int w, int h) {
        int half = h / 2;
        double topSum = 0, bottomSum = 0;
        int topCount = 0, bottomCount = 0;

        for (int row = 0; row < h; row++) {
            int rowSum = 0, rowCount = 0;
            for (int col = 0; col < w; col++) {
                if (isDark(img.getRGB(x + col, y + row))) {
                    rowSum += col;
                    rowCount++;
                }
            }
            if (rowCount == 0) continue;
            double centroid = (double) rowSum / rowCount;
            if (row < half) {
                topSum += centroid;
                topCount++;
            } else {
                bottomSum += centroid;
                bottomCount++;
            }
        }
        if (topCount == 0 || bottomCount == 0) return 0;
        double top = topSum / topCount;
        double bottom = bottomSum / bottomCount;
        return (top - bottom) / Math.max(1, w);
    }

    static boolean isDark(int rgb) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        return r + g + b < 360;
    }
}