package ru.ticket.svg;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import ru.ticket.email.render.BatikSlideRasterizer;

class TransparentPaintTest {
    @Test void transparentPaintSurvivesActualEmailRasterization() throws Exception {
        String source = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"20\" viewBox=\"0 0 40 20\">"
                + "<rect width=\"20\" height=\"20\" fill=\"transparent\" stroke=\"TRANSPARENT\"/>"
                + "<rect x=\"20\" width=\"20\" height=\"20\" style=\"fill:blue;stroke:transparent\"/>"
                + "<text x=\"0\" y=\"15\" fill=\"transparent\">transparent</text></svg>";
        String clean = SvgSanitizer.sanitize(source);
        assertTrue(clean.contains("stroke=\"none\""));
        assertTrue(clean.contains("stroke:none"));
        assertTrue(clean.contains(">transparent</text>"));
        var bytes = new BatikSlideRasterizer().rasterizePng(clean, 1);
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(image);
        assertEquals(0, image.getRGB(10, 10) >>> 24, "Transparent region must remain transparent");
        assertEquals(0xff0000ff, image.getRGB(30, 10), "Opaque paint must retain its color");
    }
}
