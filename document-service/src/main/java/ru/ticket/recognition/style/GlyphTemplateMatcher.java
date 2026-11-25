package ru.ticket.recognition.style;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ru.ticket.recognition.OcrEngine.Word;

/** Compares glyph silhouettes and aspect ratios instead of choosing a font by word width alone. */
final class GlyphTemplateMatcher {
    private static final int SAMPLE_WIDTH = 96, SAMPLE_HEIGHT = 32, REFERENCE_SIZE = 64;
    private record Key(String text, String family, int style) {}
    private record Sample(boolean[] pixels, double aspect, int height) {}
    private static final Map<Key, Sample> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Sample> entry) { return size() > 256; }
    });

    private GlyphTemplateMatcher() {}

    static FontMatch match(BufferedImage image, Word word, List<String> families, FontMatch fallback) {
        if (word.text() == null || word.text().isBlank() || word.text().length() > 128) return fallback;
        var mask = ForegroundMask.normalize(image, word.x(), word.y(), word.width(), word.height());
        Sample target = sample(mask);
        if (target == null) return fallback;
        double best = Double.POSITIVE_INFINITY;
        FontMatch result = fallback;
        for (String family : families) for (int style : new int[]{Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC}) {
            var key = new Key(word.text(), family, style);
            Sample template = CACHE.get(key);
            if (template == null) {
                template = render(key);
                if (template == null) continue;
                CACHE.put(key, template);
            }
            int union = 0, difference = 0;
            for (int i = 0; i < target.pixels.length; i++) {
                if (target.pixels[i] || template.pixels[i]) union++;
                if (target.pixels[i] != template.pixels[i]) difference++;
            }
            double error = (double) difference / Math.max(1, union)
                    + Math.abs(Math.log(target.aspect / template.aspect)) * .5;
            if (error < best) {
                best = error;
                double heightSize = REFERENCE_SIZE * (double) target.height / template.height;
                double widthSize = heightSize * target.aspect / template.aspect;
                double size = Math.min(heightSize, widthSize);
                result = new FontMatch(family, Math.max(8, Math.min(512, size)),
                        (style & Font.BOLD) != 0, (style & Font.ITALIC) != 0, error < .45);
            }
        }
        return result;
    }

    private static Sample render(Key key) {
        var probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        var g = probe.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            var font = new Font(ru.ticket.text.FontFamilyResolver.resolve(key.family), key.style, REFERENCE_SIZE);
            if (font.canDisplayUpTo(key.text) >= 0) return null;
            var glyphs = font.createGlyphVector(g.getFontRenderContext(), key.text);
            Rectangle bounds = glyphs.getPixelBounds(g.getFontRenderContext(), 0, 0);
            if (bounds.width <= 0 || bounds.height <= 0 || bounds.width > 16384) return null;
            var rendered = new BufferedImage(bounds.width + 4, bounds.height + 4, BufferedImage.TYPE_INT_RGB);
            var painter = rendered.createGraphics();
            try {
                painter.setColor(Color.WHITE); painter.fillRect(0, 0, rendered.getWidth(), rendered.getHeight());
                painter.setColor(Color.BLACK);
                painter.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                painter.drawGlyphVector(glyphs, 2 - bounds.x, 2 - bounds.y);
            } finally { painter.dispose(); }
            return sample(rendered);
        } finally { g.dispose(); }
    }

    private static Sample sample(BufferedImage mask) {
        int left = mask.getWidth(), top = mask.getHeight(), right = -1, bottom = -1;
        for (int y = 0; y < mask.getHeight(); y++) for (int x = 0; x < mask.getWidth(); x++) {
            if ((mask.getRGB(x, y) & 255) >= 128) continue;
            left = Math.min(left, x); top = Math.min(top, y);
            right = Math.max(right, x); bottom = Math.max(bottom, y);
        }
        if (right < left) return null;
        int width = right - left + 1, height = bottom - top + 1;
        var pixels = new boolean[SAMPLE_WIDTH * SAMPLE_HEIGHT];
        for (int y = 0; y < SAMPLE_HEIGHT; y++) for (int x = 0; x < SAMPLE_WIDTH; x++) {
            int px = left + Math.min(width - 1, (int)((x + .5) * width / SAMPLE_WIDTH));
            int py = top + Math.min(height - 1, (int)((y + .5) * height / SAMPLE_HEIGHT));
            pixels[y * SAMPLE_WIDTH + x] = (mask.getRGB(px, py) & 255) < 128;
        }
        return new Sample(pixels, (double) width / height, height);
    }
}
