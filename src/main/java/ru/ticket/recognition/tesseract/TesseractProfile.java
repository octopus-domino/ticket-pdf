package ru.ticket.recognition.tesseract;

import java.util.List;

/** Two complementary layouts: automatic page blocks and sparse labels inside dense forms. */
public final class TesseractProfile {
  private TesseractProfile() {}

  /** LSTM and explicit DPI prevent inconsistent defaults; polarity is prepared before either pass. */
  public static List<String> arguments(String language, boolean sparse) {
    return List.of("-l", language, "--oem", "1", "--psm", sparse ? "11" : "3",
        "--dpi", "300", "-c", "preserve_interword_spaces=1", sparse ? "hocr" : "tsv");
  }
}
