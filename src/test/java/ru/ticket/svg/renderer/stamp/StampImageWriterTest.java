package ru.ticket.svg.renderer.stamp;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import ru.ticket.dto.StampImageItem;

class StampImageWriterTest {
    private final StampImageWriter writer = new StampImageWriter();

    private StampImageItem image() {
        var image = new StampImageItem();
        image.src = "data:image/png;base64,AA==";
        image.x = 10.5;
        image.y = 20.25;
        image.width = 80;
        image.height = 40;
        return image;
    }

    @Test void preservesRotationAndFitForEverySupportedMode() {
        var image = image();
        image.rotation = 35.5;
        String[] modes = {"contain", "cover", "fill"};
        String[] aspects = {"xMidYMid meet", "xMidYMid slice", "none"};
        for (int i = 0; i < modes.length; i++) {
            image.fit = modes[i];
            var out = new StringBuilder();
            writer.write(out, image);
            assertTrue(out.toString().contains("rotate(35.50 10.50 20.25)"));
            assertTrue(out.toString().contains("preserveAspectRatio=\"" + aspects[i] + "\""));
            assertTrue(out.toString().contains("overflow=\"hidden\""));
        }
    }

    @Test void supportsLegacyNullFieldsAndEscapesImageSource() {
        var image = image();
        image.rotation = null;
        image.fit = null;
        image.src = "logo?a=1&b=\"test\"";
        var out = new StringBuilder();
        writer.write(out, image);
        assertFalse(out.toString().contains("rotate("));
        assertTrue(out.toString().contains("xMidYMid meet"));
        assertTrue(out.toString().contains("&amp;b=&quot;test&quot;"));
    }

    @Test void omitsHiddenAndMissingImages() {
        var image = image();
        image.hidden = true;
        var out = new StringBuilder();
        writer.write(out, image);
        image.hidden = false;
        image.src = null;
        writer.write(out, image);
        image.src = " ";
        writer.write(out, image);
        assertTrue(out.isEmpty());
    }
}
