package ru.ticket.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Единая точка маппинга фронтового {@code QrFormat} в ZXing.
 *
 * EncodeHintType.ERROR_CORRECTION имеет разный тип и семантику для каждого
 * BarcodeFormat:
 *   • QR_CODE     → ErrorCorrectionLevel enum (L/M/Q/H)
 *   • AZTEC       → Integer — процент EC codewords (0..100)
 *   • PDF_417     → Integer — уровень коррекции 0..8
 *   • DATA_MATRIX → hint не передаётся (коррекция автоматическая)
 *
 * Если положить один и тот же тип во все форматы — падает:
 *   AztecWriter делает Integer.parseInt(hint.toString());
 *   PDF417Writer делает (Integer) hint.
 */
public final class QrFormats {

    private QrFormats() {}

    public static BarcodeFormat toBarcodeFormat(String format) {
        if (format == null || format.isBlank()) return BarcodeFormat.QR_CODE;
        return switch (format.toUpperCase(Locale.ROOT)) {
            case "QR", "QR_CODE"     -> BarcodeFormat.QR_CODE;
            case "DATA_MATRIX"       -> BarcodeFormat.DATA_MATRIX;
            case "AZTEC"             -> BarcodeFormat.AZTEC;
            case "PDF_417", "PDF417" -> BarcodeFormat.PDF_417;
            default                  -> BarcodeFormat.QR_CODE;
        };
    }

    public static Map<EncodeHintType, Object> hintsFor(
            BarcodeFormat barcodeFormat,
            String ecc,
            int margin
    ) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, margin);

        switch (barcodeFormat) {
            case QR_CODE -> hints.put(EncodeHintType.ERROR_CORRECTION, parseQrEcc(ecc));
            case AZTEC   -> hints.put(EncodeHintType.ERROR_CORRECTION, parseAztecEcc(ecc));
            case PDF_417 -> hints.put(EncodeHintType.ERROR_CORRECTION, parsePdf417Ecc(ecc));
            default      -> { /* DATA_MATRIX: коррекция автоматическая */ }
        }
        return hints;
    }

    private static ErrorCorrectionLevel parseQrEcc(String ecc) {
        if (ecc == null || ecc.isBlank()) return ErrorCorrectionLevel.M;
        try {
            return ErrorCorrectionLevel.valueOf(ecc.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ErrorCorrectionLevel.M;
        }
    }

    /**
     * Aztec: Integer — процент EC codewords, 0..100.
     * Приблизительное соответствие L/M/Q/H — у Aztec своя шкала.
     */
    private static Integer parseAztecEcc(String ecc) {
        if (ecc == null || ecc.isBlank()) return 33;
        return switch (ecc.toUpperCase(Locale.ROOT)) {
            case "L" -> 23;
            case "Q" -> 45;
            case "H" -> 55;
            default  -> 33;
        };
    }

    /**
     * PDF417: Integer — уровень 0..8.
     * Приблизительное соответствие L/M/Q/H → 2/4/6/8.
     */
    private static Integer parsePdf417Ecc(String ecc) {
        if (ecc == null || ecc.isBlank()) return 4;
        return switch (ecc.toUpperCase(Locale.ROOT)) {
            case "L" -> 2;
            case "Q" -> 6;
            case "H" -> 8;
            default  -> 4;
        };
    }
}