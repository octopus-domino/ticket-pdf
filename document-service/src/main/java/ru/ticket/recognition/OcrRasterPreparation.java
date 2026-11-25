package ru.ticket.recognition;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import ru.ticket.recognition.OcrEngine.Word;

/** OCR-only enlargement keeps the original colour raster authoritative for layout and artwork. */
public final class OcrRasterPreparation implements AutoCloseable {
    private static final int SMALL_LONG_SIDE = 1600;
    private static final int TARGET_LONG_SIDE = 2400;
    private static final int PALE_PAPER_THRESHOLD = 225;
    private final BufferedImage original;
    private final BufferedImage image;
    private final double scale;

    public OcrRasterPreparation(BufferedImage source) {
        original = source;
        int longest = Math.max(source.getWidth(), source.getHeight());
        scale = longest < SMALL_LONG_SIDE ? Math.min(3, (double) TARGET_LONG_SIDE / longest) : 1;
        image = scale == 1 ? source : enlarge(source);
    }

    public BufferedImage image() { return image; }

    /** Returns OCR coordinates to the unchanged source image before table/style analysis. */
    public List<Word> restore(List<Word> words) {
        if (scale == 1) return words;
        return words.stream().map(word -> new Word(word.text(), (int) Math.round(word.x() / scale),
            (int) Math.round(word.y() / scale), Math.max(1, (int) Math.round(word.width() / scale)),
            Math.max(1, (int) Math.round(word.height() / scale)), word.confidence(), word.line())).toList();
    }

    private BufferedImage enlarge(BufferedImage source) {
        var paper = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            int rgb = source.getRGB(x, y);
            int shade = ((rgb >> 16 & 255) * 299 + (rgb >> 8 & 255) * 587 + (rgb & 255) * 114) / 1000;
            if (shade > PALE_PAPER_THRESHOLD) shade = 255;
            paper.setRGB(x, y, shade << 16 | shade << 8 | shade);
        }
        var result = new BufferedImage((int) Math.round(source.getWidth() * scale),
            (int) Math.round(source.getHeight() * scale), BufferedImage.TYPE_INT_RGB);
        var graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(paper, 0, 0, result.getWidth(), result.getHeight(), null);
        } finally { graphics.dispose(); paper.flush(); }
        return result;
    }

    @Override public void close() { if (image != original) image.flush(); }
}
