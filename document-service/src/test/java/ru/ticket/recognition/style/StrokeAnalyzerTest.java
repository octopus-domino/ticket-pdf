package ru.ticket.recognition.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class StrokeAnalyzerTest {

    @Test
    void medianRunLengthOnSolidBarEqualsBarWidth() {
        BufferedImage bar = TestImages.solidBlack(100, 10);
        assertThat(StrokeAnalyzer.medianHorizontalRunLength(bar, 0, 0, 100, 10))
                .isEqualTo(100.0);
    }

    @Test
    void medianRunLengthOnTwoBarsEqualsBarWidth() {
        BufferedImage img = TestImages.blank(100, 10, Color.WHITE);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, 20, 10);
            g.fillRect(60, 0, 20, 10);
        } finally {
            g.dispose();
        }
        assertThat(StrokeAnalyzer.medianHorizontalRunLength(img, 0, 0, 100, 10))
                .isEqualTo(20.0);
    }

    @Test
    void xHeightOnLowercaseIsLessThanBoxHeight() {
        var rendered = TestImages.renderWord("aaaa", "SansSerif", 40,
                Font.PLAIN, Color.BLACK, 2, 2);
        double xh = StrokeAnalyzer.xHeight(
                rendered.image(), rendered.x(), rendered.y(),
                rendered.width(), rendered.height());
        assertThat(xh).isLessThan(rendered.height());
        assertThat(xh).isGreaterThan(rendered.height() * 0.4);
    }

    @Test
    void xHeightOnSolidBarEqualsBoxHeight() {
        BufferedImage bar = TestImages.solidBlack(50, 20);
        assertThat(StrokeAnalyzer.xHeight(bar, 0, 0, 50, 20)).isEqualTo(20.0);
    }

    @Test
    void detectsUnderlineDrawnBeneathText() {
        BufferedImage img = TestImages.blank(120, 40, Color.WHITE);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.BLACK);
            g.setFont(new Font("SansSerif", Font.PLAIN, 20));
            g.drawString("Hi", 5, 20);
            g.fillRect(5, 33, 100, 2);
        } finally {
            g.dispose();
        }
        assertThat(StrokeAnalyzer.hasUnderline(img, 5, 5, 100, 30)).isTrue();
    }

    @Test
    void noUnderlineOnBareText() {
        var rendered = TestImages.renderWord("Hi", "SansSerif", 20,
                Font.PLAIN, Color.BLACK, 2, 2);
        assertThat(StrokeAnalyzer.hasUnderline(
                rendered.image(), rendered.x(), rendered.y(),
                rendered.width(), rendered.height())).isFalse();
    }

    @Test
    void skewedParallelogramHasPositiveItalicSkew() {
        BufferedImage img = TestImages.blank(50, 50, Color.WHITE);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.BLACK);
            g.fillPolygon(new Polygon(
                    new int[]{30, 40, 20, 10},
                    new int[]{0, 0, 50, 50}, 4));
        } finally {
            g.dispose();
        }
        assertThat(StrokeAnalyzer.italicSkew(img, 0, 0, 50, 50)).isPositive();
    }

    @Test
    void darkThresholdIsDocumented() {
        assertThat(StrokeAnalyzer.isDark(0x000000)).isTrue();
        assertThat(StrokeAnalyzer.isDark(0x404040)).isTrue();
        assertThat(StrokeAnalyzer.isDark(0x808080)).isFalse();
        assertThat(StrokeAnalyzer.isDark(0xFFFFFF)).isFalse();
    }
}