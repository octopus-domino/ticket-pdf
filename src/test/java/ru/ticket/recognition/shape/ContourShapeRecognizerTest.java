package ru.ticket.recognition.shape;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class ContourShapeRecognizerTest {

    private final ContourShapeRecognizer recognizer = new ContourShapeRecognizer();

    @Test
    void detectsRectangle() {
        BufferedImage img = blank(200, 150);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.BLACK);
            g.fillRect(20, 20, 120, 80);
        } finally {
            g.dispose();
        }
        var shapes = recognizer.recognize(img, new boolean[200 * 150]);
        assertThat(shapes).anyMatch(s -> "shape".equals(s.type())
                && "rect".equals(s.properties().get("shape")));
    }

    @Test
    void detectsCircle() {
        BufferedImage img = blank(200, 200);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.BLUE);
            g.fillOval(40, 40, 120, 120);
        } finally {
            g.dispose();
        }
        var shapes = recognizer.recognize(img, new boolean[200 * 200]);
        assertThat(shapes).anyMatch(s -> "shape".equals(s.type())
                && "circle".equals(s.properties().get("shape")));
    }

    @Test
    void textMaskExcludesGlyphsFromShapeDetection() {
        BufferedImage img = blank(200, 100);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.BLACK);
            g.fillRect(20, 20, 100, 60);   // фигура
            g.drawString("TEXT", 150, 50); // текст
        } finally {
            g.dispose();
        }
        boolean[] mask = new boolean[200 * 100];
        // Маскируем область текста
        for (int y = 30; y < 70; y++) {
            for (int x = 140; x < 195; x++) {
                mask[y * 200 + x] = true;
            }
        }
        var shapes = recognizer.recognize(img, mask);
        // Фигура найдена, текст — нет
        assertThat(shapes).isNotEmpty();
        assertThat(shapes).noneMatch(s -> s.x() > 140);
    }

    @Test
    void tinyNoiseIsIgnored() {
        BufferedImage img = blank(100, 100);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.BLACK);
            g.fillRect(50, 50, 3, 3);
        } finally {
            g.dispose();
        }
        assertThat(recognizer.recognize(img, new boolean[100 * 100])).isEmpty();
    }

    private static BufferedImage blank(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
        } finally {
            g.dispose();
        }
        return img;
    }
}