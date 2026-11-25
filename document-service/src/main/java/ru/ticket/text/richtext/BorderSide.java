package ru.ticket.text.richtext;

public record BorderSide(String color, double width, String style) {

    public boolean isVisible() {
        return width > 0
                && style != null
                && !"none".equalsIgnoreCase(style);
    }
}