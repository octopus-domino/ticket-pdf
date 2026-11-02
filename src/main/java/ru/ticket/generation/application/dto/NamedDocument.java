package ru.ticket.generation.application.dto;

/** Документ с именем файла — результат рендера одного item. */
public record NamedDocument(String filename, byte[] content) { }