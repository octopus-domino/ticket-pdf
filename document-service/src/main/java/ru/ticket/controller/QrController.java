package ru.ticket.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.ticket.qr.QrFormats;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;

@RestController
@RequestMapping("/api/qr")
public class QrController {

    @GetMapping(produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] generateQr(
            @RequestParam String text,
            @RequestParam(defaultValue = "300") int size,
            @RequestParam(required = false) String fg,
            @RequestParam(required = false) String bg,
            @RequestParam(defaultValue = "4") int margin,
            @RequestParam(defaultValue = "M") String ecc,
            @RequestParam(defaultValue = "QR") String format
    ) throws Exception {
        String foreground = normalizeColor(fg, "#000000");
        String background = normalizeColor(bg, "#ffffff");

        BarcodeFormat barcodeFormat = QrFormats.toBarcodeFormat(format);
        Map<EncodeHintType, Object> hints = QrFormats.hintsFor(barcodeFormat, ecc, margin);

        BitMatrix matrix = new MultiFormatWriter().encode(text, barcodeFormat, size, size, hints);
        int width = matrix.getWidth();
        int height = matrix.getHeight();

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(parseHex(background));
            g.fillRect(0, 0, width, height);
            g.setColor(parseHex(foreground));
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (matrix.get(x, y)) {
                        g.fillRect(x, y, 1, 1);
                    }
                }
            }
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", out);
        return out.toByteArray();
    }

    private static String normalizeColor(String v, String fallback) {
        if (v == null || v.isBlank()) return fallback;
        String s = v.trim();
        if (s.startsWith("#") && (s.length() == 4 || s.length() == 7)) return s;
        return fallback;
    }

    private static Color parseHex(String hex) {
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) {
            s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        }
        int rgb = Integer.parseInt(s, 16);
        return new Color(rgb);
    }
}