package ru.ticket.text;

import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

@Service
public class AwtTextLayoutService implements TextLayoutService {

    private static final BufferedImage MEASURE_IMAGE =
            new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    @Override
    public List<String> wrap(String text, double maxWidth,
                             String fontFamily, int fontSize, String fontStyle) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            result.add("");
            return result;
        }

        Graphics2D g = MEASURE_IMAGE.createGraphics();
        try {
            g.setFont(buildFont(fontFamily, fontSize, fontStyle));
            FontMetrics fm = g.getFontMetrics();

            for (String paragraph : text.split("\n", -1)) {
                wrapParagraph(paragraph, maxWidth, fm, result);
            }
        } finally {
            g.dispose();
        }
        return result;
    }

    @Override
    public double measure(String text, String fontFamily,
                          int fontSize, String fontStyle) {
        Graphics2D g = MEASURE_IMAGE.createGraphics();
        try {
            g.setFont(buildFont(fontFamily, fontSize, fontStyle));
            return g.getFontMetrics().stringWidth(text);
        } finally {
            g.dispose();
        }
    }

    private void wrapParagraph(String paragraph, double maxWidth,
                               FontMetrics fm, List<String> out) {
        if (paragraph.isEmpty()) {
            out.add("");
            return;
        }

        String[] words = paragraph.split(" ");
        StringBuilder current = new StringBuilder();
        double currentWidth = 0;

        for (String word : words) {
            double wordWidth = fm.stringWidth(word);
            double spaceWidth = current.length() > 0 ? fm.stringWidth(" ") : 0;

            if (currentWidth + spaceWidth + wordWidth <= maxWidth
                    || current.length() == 0) {
                if (current.length() > 0) {
                    current.append(' ');
                    currentWidth += spaceWidth;
                }
                current.append(word);
                currentWidth += wordWidth;
            } else {
                out.add(current.toString());
                current = new StringBuilder(word);
                currentWidth = wordWidth;
            }
        }

        if (current.length() > 0) out.add(current.toString());
    }

    private Font buildFont(String family, int size, String style) {
        int s = Font.PLAIN;
        if (style != null) {
            if (style.contains("bold"))   s |= Font.BOLD;
            if (style.contains("italic")) s |= Font.ITALIC;
        }
        return new Font(family != null ? family : "Arial", s, size);
    }
}