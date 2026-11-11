package ru.ticket.recognition.tesseract;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;

/**
 * Единственная точка запуска процесса {@code tesseract}.
 *
 * <p><b>SRP.</b> Знает ровно три вещи: подготовить временную директорию, запустить процесс с
 * таймаутом, убрать за собой. Ни парсинга TSV, ни парсинга hOCR.
 *
 * <p><b>Почему файлы, а не pipe.</b> Tesseract на плотных страницах выдаёт мегабайты; pipe-буфер ОС
 * ограничен — без redirect был бы классический deadlock «процесс ждёт читателя, читатель ждёт
 * процесса».
 */
@Component
public class TesseractProcessRunner {

  private static final long TIMEOUT_SECONDS = 60L;

  /**
   * @param extraArgs аргументы CLI после {@code input stdout}
   * @param maxOutputBytes предел на размер вывода (TSV/hOCR)
   */
  public record Request(List<String> extraArgs, long maxOutputBytes) {}

  private final String executable;

  public TesseractProcessRunner(
      @Value("${app.recognition.tesseract:tesseract}") String executable) {
    this.executable = executable;
  }

  public String run(BufferedImage image, Request request) {
    Path directory = null;
    Process process = null;
    try {
      directory = Files.createTempDirectory("ticket-ocr-");
      Path input = directory.resolve("input.png");
      Path output = directory.resolve("result.txt");
      Path error = directory.resolve("error.txt");

      BufferedImage prepared = TextPolarityNormalizer.prepare(image);
      try {
        ImageIO.write(prepared, "png", input.toFile());
      } finally {
        if (prepared != image) prepared.flush();
      }

      var command = new ArrayList<String>();
      command.add(executable);
      command.add(input.toString());
      command.add("stdout");
      command.addAll(request.extraArgs());

      var builder =
          new ProcessBuilder(command).redirectOutput(output.toFile()).redirectError(error.toFile());
      // Tesseract сам использует OpenMP; на плотных страницах это
      // приводит к флаппингу памяти. Один поток — предсказуемо.
      builder.environment().put("OMP_THREAD_LIMIT", "1");

      process = builder.start();

      if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
        throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "recognition.timeout");
      }
      if (process.exitValue() != 0) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "recognition.ocr_unavailable");
      }
      if (Files.size(output) > request.maxOutputBytes()) {
        throw ApiException.badRequest("recognition.too_complex");
      }
      return Files.readString(output, StandardCharsets.UTF_8);

    } catch (IOException error) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "recognition.ocr_unavailable");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "recognition.interrupted");
    } finally {
      if (process != null && process.isAlive()) process.destroyForcibly();
      cleanup(directory);
    }
  }

  private static void cleanup(Path directory) {
    if (directory == null) return;
    try (var paths = Files.list(directory)) {
      for (Path file : paths.toList()) {
        try {
          Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
      }
    } catch (IOException ignored) {
    }
    try {
      Files.deleteIfExists(directory);
    } catch (IOException ignored) {
    }
  }
}
