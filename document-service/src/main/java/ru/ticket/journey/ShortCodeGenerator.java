package ru.ticket.journey;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Uniform 12-character Base62 codes: ~71 bits of entropy, independent of counters and time. */
@Component
public class ShortCodeGenerator {
  private static final String ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private final SecureRandom random = new SecureRandom();

  public String next() {
    var code = new StringBuilder(12);
    for (int i = 0; i < 12; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    return code.toString();
  }
}
