package ru.ticket.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.ticket.common.ApiException;

/**
 * Юнит-тест фасада {@link ImageRecognitionService}.
 *
 * <p>Проверяет роутинг между движками (Tesseract и Ollama),
 * валидацию входных параметров и защиту от перегрузки (семафор).
 *
 * <p>Использует Mockito для изоляции от реальных процессов Tesseract
 * и сетевых запросов к Ollama — тест выполняется за миллисекунды
 * и не требует установленного ПО.
 */
class RecognitionTest {

  private ImageDecoder decoder;
  private CompositeRecognitionService composite;
  private LocalVisionRecognizer vision;
  private ImageRecognitionService service;

  @BeforeEach
  void setUp() {
    decoder = mock(ImageDecoder.class);
    composite = mock(CompositeRecognitionService.class);
    vision = mock(LocalVisionRecognizer.class);
    service = new ImageRecognitionService(decoder, composite, vision);
  }

  @Test
  void routesToCompositeWhenEngineIsOcr() {
    // given
    BufferedImage dummyImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    when(decoder.decode(anyString())).thenReturn(dummyImage);

    RecognitionDraft expectedDraft = new RecognitionDraft(
            10, 10, List.of(), List.of("recognition.review_required"), "dual");
    when(composite.recognize(any(), eq("eng"))).thenReturn(expectedDraft);

    // when
    RecognitionDraft result = service.recognize("data:image/png;base64,123", "eng", "ocr");

    // then
    assertThat(result).isEqualTo(expectedDraft);
    verify(composite).recognize(dummyImage, "eng");
  }

  @Test
  void routesToVisionWhenEngineIsOllama() {
    // given
    BufferedImage dummyImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    when(decoder.decode(anyString())).thenReturn(dummyImage);

    RecognitionDraft expectedDraft = new RecognitionDraft(
            10, 10, List.of(), List.of("recognition.review_required"), "ollama");
    when(vision.recognize(any())).thenReturn(expectedDraft);

    // when
    RecognitionDraft result = service.recognize("data:image/png;base64,123", "eng", "ollama");

    // then
    assertThat(result).isEqualTo(expectedDraft);
    verify(vision).recognize(dummyImage);
  }

  @Test
  void rejectsInvalidLanguage() {
    assertThatThrownBy(() -> service.recognize("data", "fra", "ocr"))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.status()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(apiEx.getMessage()).contains("recognition.invalid_request");
            });
  }

  @Test
  void rejectsInvalidEngine() {
    assertThatThrownBy(() -> service.recognize("data", "eng", "magic"))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.status()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(apiEx.getMessage()).contains("recognition.invalid_request");
            });
  }

  @Test
  void returnsTooManyRequestsWhenSemaphoreIsBusy() throws Exception {
    // Семафор в ImageRecognitionService имеет 1 разрешение.
    // Занимаем его из другого потока, который будет держать его 200 мс.
    ExecutorService executor = Executors.newSingleThreadExecutor();
    when(decoder.decode(anyString())).thenReturn(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));
    // Заставляем композит "висеть" 200мс, удерживая семафор
    when(composite.recognize(any(), anyString())).thenAnswer(inv -> {
      Thread.sleep(200);
      return new RecognitionDraft(10, 10, List.of(), List.of(), "dual");
    });

    CompletableFuture<Void> backgroundRequest = CompletableFuture.runAsync(() -> {
      try {
        service.recognize("data:image/png;base64,123", "eng", "ocr");
      } catch (Exception ignored) {
      }
    }, executor);

    // Даём фоновому потоку время захватить семафор
    Thread.sleep(50);

    // when / then
    assertThatThrownBy(() -> service.recognize("data:image/png;base64,123", "eng", "ocr"))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
              assertThat(apiEx.getMessage()).contains("recognition.busy");
            });

    backgroundRequest.get(1, TimeUnit.SECONDS);
    executor.shutdownNow();
  }
}