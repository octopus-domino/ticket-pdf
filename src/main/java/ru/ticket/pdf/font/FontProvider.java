package ru.ticket.pdf.font;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.IOException;

public interface FontProvider {
    PDFont getFont(PDDocument doc, String family) throws IOException;
    String resolveFamily(String requested);
}