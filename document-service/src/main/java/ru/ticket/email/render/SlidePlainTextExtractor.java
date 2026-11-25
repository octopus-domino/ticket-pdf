package ru.ticket.email.render;

import org.springframework.stereotype.Component;
import ru.ticket.dto.Element;
import ru.ticket.dto.FooterElement;
import ru.ticket.dto.Slide;
import ru.ticket.dto.TableElement;
import ru.ticket.dto.TextElement;

import java.util.List;

/**
 * Produces the {@code text/plain} MIME alternative for a slide.
 * Purely additive: main HTML stays pixel-perfect, text version gives
 * screen-readers and plain-text clients something readable.
 */
@Component
public class SlidePlainTextExtractor {

    private static final String FALLBACK = "Документ";

    public String extract(Slide slide) {
        if (slide == null || slide.elements == null) return FALLBACK;

        StringBuilder sb = new StringBuilder();
        for (Element el : slide.elements) {
            append(sb, el);
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? FALLBACK : result;
    }

    private void append(StringBuilder sb, Element el) {
        if (el instanceof TextElement t && t.text != null && !t.text.isBlank()) {
            sb.append(t.text).append("\n\n");
        } else if (el instanceof FooterElement f && f.text != null && !f.text.isBlank()) {
            sb.append(f.text).append("\n");
        } else if (el instanceof TableElement tb && tb.data != null) {
            for (List<String> row : tb.data) {
                if (row == null) continue;
                for (String cell : row) {
                    if (cell != null && !cell.isBlank()) sb.append(cell).append(' ');
                }
                sb.append('\n');
            }
        }
    }
}