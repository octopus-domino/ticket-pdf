package ru.ticket.schema.domain;

/**
 * Описание поля схемы.
 * record — Jackson в Spring Boot 3 сериализует в JSON-колонку без танцев.
 * Поле `path` совпадает с {{...}} и PlaceholderElement.fieldPath.
 */
public record FieldDef(
        String path,
        String type,      // string | number | date | boolean | image | object | array
        boolean required,
        String label,
        String defaultValue
) {
    public FieldDef {
        if (type == null || type.isBlank()) type = "string";
        if (label == null || label.isBlank()) label = path;
    }
}