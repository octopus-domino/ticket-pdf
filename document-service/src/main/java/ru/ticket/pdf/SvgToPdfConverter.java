package ru.ticket.pdf;

import java.util.List;

public interface SvgToPdfConverter {

    byte[] convert(String svg, float widthPx, float heightPx) throws Exception;

    /**
     * Конвертирует список SVG в один многостраничный PDF.
     * Порядок страниц соответствует порядку svgs.
     */
    byte[] convertMultiPage(List<String> svgs) throws Exception;
}