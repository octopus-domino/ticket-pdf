package ru.ticket.recognition.style;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import ru.ticket.recognition.OcrEngine.Word;

/**
 * Рендерит реальный текст через AWT, чтобы стилевые анализаторы работали с
 * настоящим растром, а не с синтетическими заглушками.
 */
public final class TestImages {

    private TestImages() {}

    public record RenderedWord(
            String text,
            BufferedImage image,
            int x, int y, int width, int height) {

        public Word toWord(double confidence) {
            return new Word(text, x, y, width, height, confidence, "line-1");
        }
    }

    public static BufferedImage blank(int w, int h, Color bg) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(bg);
            g.fillRect(0, 0, w, h);
        } finally {
            g.dispose();
        }
        return img;
    }

    public static BufferedImage solidBlack(int w, int h) {
        return blank(w, h, Color.BLACK);
    }

    public static RenderedWord renderWord(
            String text,
            String family,
            int fontSize,
            int fontStyle,
            Color foreground,
            int paddingX,
            int paddingY) {

        Font font = new Font(ru.ticket.text.FontFamilyResolver.resolve(family), fontStyle, fontSize);
        FontMetrics metrics = measure(font);

        int textWidth  = Math.max(1, metrics.stringWidth(text));
        int textHeight = Math.max(1, metrics.getAscent() + metrics.getDescent());
        int w = textWidth  + paddingX * 2;
        int h = textHeight + paddingY * 2;

        BufferedImage img = blank(w, h, Color.WHITE);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(font);
            g.setColor(foreground);
            g.drawString(text, paddingX, paddingY + metrics.getAscent());
        } finally {
            g.dispose();
        }

        return new RenderedWord(text, img, paddingX, paddingY, textWidth, textHeight);
    }

    private static FontMetrics measure(Font font) {
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = probe.createGraphics();
        try {
            g.setFont(font);
            return g.getFontMetrics();
        } finally {
            g.dispose();
        }
    }
}
