package ru.ticket.template.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Точка привязки «элемент шаблона → поле схемы».
 * Совместимо с frontend {@code features/bindings/domain/Binding.ts}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BindingDto {

    public String elementId;
    public String elementLabel;
    public String placeholder;
    public String schemaPath;

    /** auto | legacy | manual */
    public String source = "auto";

    /** linked | missing | invalid-type */
    public String status = "linked";

    public String inferred;

    public BindingDto() { }

    public BindingDto(String elementId,
                      String elementLabel,
                      String placeholder,
                      String schemaPath,
                      String source,
                      String status,
                      String inferred) {
        this.elementId = elementId;
        this.elementLabel = elementLabel;
        this.placeholder = placeholder;
        this.schemaPath = schemaPath;
        this.source = source == null ? "auto" : source;
        this.status = status == null ? "linked" : status;
        this.inferred = inferred == null ? schemaPath : inferred;
    }
}