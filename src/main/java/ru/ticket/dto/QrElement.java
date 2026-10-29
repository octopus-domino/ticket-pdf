package ru.ticket.dto;

/**
 * QR-элемент. На фронте — placeholder на канве.
 * На сервере — рендерится в SVG через {@code QrElementRenderer} (ZXing).
 *
 * format принимает значения: QR | DATA_MATRIX | AZTEC | PDF_417.
 */
public class QrElement extends Element {
    public String text = "";
    public String format = "QR";
    public String fgColor = "#000000";
    public String bgColor = "#ffffff";
    public Integer margin = 4;
    public String ecc = "M"; // L / M / Q / H
}