package ru.ticket.text;

import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Resolve browser font names to installed metric-compatible fonts on Linux. */
public final class FontFamilyResolver {
    private static final Set<String> INSTALLED = installed();
    private static Set<String> installed() {
      for (String file : new String[] { "MarckScript-Regular.ttf", "ZhiMangXing-Regular.ttf" }) {
        try (var input = FontFamilyResolver.class.getResourceAsStream("/fonts/" + file)) {
          if (input != null) GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, input));
        } catch (java.io.IOException | java.awt.FontFormatException ex) { throw new IllegalStateException("Cannot register handwriting font", ex); }
      }
      return Arrays.stream(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
            .map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }
    private static final Map<String, String> ALIASES = Map.of(
            "arial", "Liberation Sans",
            "times new roman", "Liberation Serif",
            "courier new", "Liberation Mono");

    private FontFamilyResolver() {}

    public static boolean available(String family) {
        return family != null && INSTALLED.contains(resolve(family).toLowerCase(Locale.ROOT));
    }

    public static String resolve(String requested) {
        if (requested == null || requested.isBlank()) return "SansSerif";
        String key = requested.toLowerCase(Locale.ROOT);
        if (INSTALLED.contains(key)) return requested;
        String alternative = ALIASES.get(key);
        return alternative != null && INSTALLED.contains(alternative.toLowerCase(Locale.ROOT))
                ? alternative : requested;
    }
}
