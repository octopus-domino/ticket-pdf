package ru.ticket.svg.renderer.stamp;

import org.junit.jupiter.api.Test;
import ru.ticket.dto.StampTextItem;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class StampTextWriterTest {
    @Test void keepsUpperAndLowerLabelsUprightInTheExport() {
        for (double[] angles : new double[][] {{-160, -20}, {155, 25}}) {
            var text = new StampTextItem();
            text.mode = "arc";
            text.text = "A";
            text.fontFamily = "Arial";
            text.fontStyle = "normal";
            text.fill = "#244f91";
            text.fontSize = 14;
            text.arcRadius = 85;
            text.arcStartDeg = angles[0];
            text.arcEndDeg = angles[1];
            var output = new StringBuilder();
            new StampTextWriter(null).write(output, text, null);
            var rotation = Pattern.compile("rotate\\(([-0-9.]+)\\)").matcher(output);
            assertTrue(rotation.find());
            assertEquals(0, Double.parseDouble(rotation.group(1)), 0.01);
        }
    }
}
