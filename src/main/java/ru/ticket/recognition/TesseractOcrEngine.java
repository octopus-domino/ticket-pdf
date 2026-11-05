package ru.ticket.recognition;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;

@Component
public class TesseractOcrEngine implements OcrEngine {
  private final String executable;
  public TesseractOcrEngine(@Value("${app.recognition.tesseract:tesseract}") String executable) {
    this.executable = executable;
  }
  @Override public List<Word> recognize(BufferedImage image, String language) {
    Path directory = null;
    Process process = null;
    try {
      directory = Files.createTempDirectory("ticket-ocr-");
      Path input = directory.resolve("input.png"), output = directory.resolve("result.tsv"), error = directory.resolve("error.txt");
      ImageIO.write(image, "png", input.toFile());
      var builder = new ProcessBuilder(executable, input.toString(), "stdout", "-l", language,
          "--psm", "3", "tsv").redirectOutput(output.toFile()).redirectError(error.toFile());
      builder.environment().put("OMP_THREAD_LIMIT", "1");
      process = builder.start();
      if (!process.waitFor(60, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
        throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "recognition.timeout");
      }
      if (process.exitValue() != 0) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "recognition.ocr_unavailable");
      if (Files.size(output) > 2_000_000) throw ApiException.badRequest("recognition.too_complex");
      return parse(Files.readString(output, StandardCharsets.UTF_8));
    } catch (java.io.IOException error) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "recognition.ocr_unavailable");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "recognition.interrupted");
    } finally {
      if (process != null && process.isAlive()) process.destroyForcibly();
      if (directory != null) {
        try (var paths = Files.list(directory)) { for (Path file : paths.toList()) Files.deleteIfExists(file); }
        catch (java.io.IOException ignored) { /* OS may still be closing a timed-out process. */ }
        try { Files.deleteIfExists(directory); } catch (java.io.IOException ignored) { }
      }
    }
  }
  static List<Word> parse(String tsv) {
    var result = new ArrayList<Word>();
    for (String row : tsv.split("\\R")) {
      String[] cells = row.split("\t", 12);
      if (cells.length != 12 || !cells[0].equals("5") || cells[11].isBlank()) continue;
      try {
        int x = Integer.parseInt(cells[6]), y = Integer.parseInt(cells[7]), w = Integer.parseInt(cells[8]), h = Integer.parseInt(cells[9]);
        if (x < 0 || y < 0 || w <= 0 || h <= 0) continue;
        result.add(new Word(cells[11].strip(), x, y, w, h, Double.parseDouble(cells[10]),
            cells[1] + ":" + cells[2] + ":" + cells[3] + ":" + cells[4]));
        if (result.size() > 1200) throw ApiException.badRequest("recognition.too_complex");
      } catch (NumberFormatException ignored) { /* Ignore malformed OCR rows, never interpret them as commands. */ }
    }
    return result;
  }
}
