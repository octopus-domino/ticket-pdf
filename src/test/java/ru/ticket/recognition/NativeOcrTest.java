package ru.ticket.recognition;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class NativeOcrTest {
  @Test void recognizesActualPixelsWhenTesseractIsInstalled() throws Exception {
    String executable = System.getProperty("ocr.executable", "tesseract");
    boolean available;
    try { var process = new ProcessBuilder(executable, "--version").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
      available = process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0; if (process.isAlive()) process.destroyForcibly(); }
    catch (java.io.IOException error) { available = false; }
    assumeTrue(available, "Native Tesseract is not installed in this environment");
    var image = new BufferedImage(1000, 250, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics(); graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 1000, 250);
    graphics.setColor(Color.BLACK); graphics.setFont(new Font("SansSerif", Font.PLAIN, 64)); graphics.drawString("INVOICE 2026", 60, 140); graphics.dispose();
    var words = new TesseractOcrEngine(executable).recognize(image, "eng");
    assertTrue(words.stream().anyMatch(word -> word.text().equals("INVOICE")), words.toString());
    assertTrue(words.stream().anyMatch(word -> word.text().equals("2026")), words.toString());
  }
}
