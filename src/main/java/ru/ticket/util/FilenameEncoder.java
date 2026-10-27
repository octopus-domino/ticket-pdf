package ru.ticket.util;

import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class FilenameEncoder {

    /**
     * Content-Disposition по RFC 5987.
     * Кириллица в HTTP-заголовках запрещена → ASCII-fallback +
     * filename* с URL-encoded UTF-8.
     */
    public String build(String rawName, String suffix) {
        String base = (rawName == null || rawName.isBlank()) ? "page" : rawName;

        String ascii = base.replaceAll("[^\\x20-\\x7E]", "_")
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .trim();
        if (ascii.isEmpty()) ascii = "page";

        String fullName = ascii + suffix;
        String utf8Name = URLEncoder.encode(base + suffix, StandardCharsets.UTF_8)
                .replace("+", "%20");

        return "attachment; filename=\"" + fullName + "\"; " +
                "filename*=UTF-8''" + utf8Name;
    }
}