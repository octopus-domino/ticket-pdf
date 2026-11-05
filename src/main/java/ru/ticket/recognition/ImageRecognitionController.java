package ru.ticket.recognition;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/recognition","/api/guest/recognition"})
public class ImageRecognitionController {
  private final ImageRecognitionService service;
  private final LocalVisionRecognizer vision;
  public ImageRecognitionController(ImageRecognitionService service, LocalVisionRecognizer vision) { this.service = service; this.vision = vision; }
  public record Request(@NotBlank @Size(max=11_200_000) String image,
      @NotBlank @Pattern(regexp="eng|rus\\+eng") String language,
      @NotBlank @Pattern(regexp="ocr|ollama") String engine) {}
  @GetMapping("/capabilities") public Map<String, Object> capabilities() {
    return Map.of("ocr", true, "ollama", vision.enabled(), "model", vision.enabled() ? vision.model() : "");
  }
  @PostMapping public RecognitionDraft recognize(@Valid @RequestBody Request request) {
    return service.recognize(request.image(), request.language(), request.engine());
  }
}
