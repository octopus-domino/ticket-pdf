package ru.ticket.svg.renderer;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.springframework.stereotype.Component;
import ru.ticket.dto.QrElement;
import ru.ticket.qr.QrFormats;
import ru.ticket.svg.SvgContext;
import ru.ticket.util.XmlEscape;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

@Component
public class QrElementRenderer implements ElementRenderer<QrElement> {

    @Override
    public Class<QrElement> supports() { return QrElement.class; }

    @Override
    public void render(QrElement q, SvgContext ctx) {
        StringBuilder out = ctx.body();

        String tr = q.rotation != 0
                ? String.format(Locale.ROOT, " transform=\"rotate(%.2f %.2f %.2f)\"",
                (double) q.rotation, (double) q.x, (double) q.y)
                : "";

        out.append("<g").append(tr).append(">\n");

        String dataUrl = buildQrDataUrl(q);
        if (dataUrl == null) {
            out.append(String.format(Locale.ROOT,
                    "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" " +
                            "fill=\"none\" stroke=\"#cbd5e1\" stroke-width=\"1\" stroke-dasharray=\"6,4\"/>\n",
                    (double) q.x, (double) q.y, (double) q.width, (double) q.height));
        } else {
            String u = XmlEscape.attr(dataUrl);
            out.append(String.format(Locale.ROOT,
                    "<image href=\"%s\" xlink:href=\"%s\" x=\"%.2f\" y=\"%.2f\" " +
                            "width=\"%.2f\" height=\"%.2f\" preserveAspectRatio=\"xMidYMid meet\"/>\n",
                    u, u, (double) q.x, (double) q.y, (double) q.width, (double) q.height));
        }

        out.append("</g>\n");
    }

    private String buildQrDataUrl(QrElement q) {
        try {
            String text = q.text == null ? "" : q.text;
            if (text.isBlank()) return null;

            String fg = q.fgColor != null ? q.fgColor : "#000000";
            String bg = q.bgColor != null ? q.bgColor : "#ffffff";
            int margin = q.margin != null ? Math.max(0, q.margin) : 4;

            BarcodeFormat barcodeFormat = QrFormats.toBarcodeFormat(q.format);
            Map<EncodeHintType, Object> hints = QrFormats.hintsFor(barcodeFormat, q.ecc, margin);

            // 800px внутреннего разрешения достаточно для печати QR любого формата.
            int size = 800;
            BitMatrix matrix = new MultiFormatWriter().encode(text, barcodeFormat, size, size, hints);
            int w = matrix.getWidth();
            int h = matrix.getHeight();

            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setColor(parseHex(bg));
                g.fillRect(0, 0, w, h);
                g.setColor(parseHex(fg));
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if (matrix.get(x, y)) {
                            g.fillRect(x, y, 1, 1);
                        }
                    }
                }
            } finally {
                g.dispose();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            System.err.println("[QrElementRenderer] generation failed: " + e.getMessage());
            return null;
        }
    }

    private static Color parseHex(String hex) {
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) {
            s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        }
        return new Color(Integer.parseInt(s, 16));
    }
}