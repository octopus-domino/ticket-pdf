package ru.ticket.generation.application.dto;

/** Результат упаковки: байты + имя файла + mime. */
public record PackResult(byte[] content, String filename, String mimeType) { }