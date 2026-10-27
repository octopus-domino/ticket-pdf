package ru.ticket.text;

import java.util.List;

public interface TextLayoutService {
    List<String> wrap(String text, double maxWidth,
                      String fontFamily, int fontSize, String fontStyle);

    double measure(String text, String fontFamily, int fontSize, String fontStyle);
}