package ru.ticket.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class ExportRequest {

    public static final int MAX_SLIDES = 500;
    public static final int MAX_ELEMENTS_PER_SLIDE = 2000;

    @NotNull(message = "slides must not be null")
    @Size(min = 1, max = MAX_SLIDES,
            message = "slides size must be between 1 and " + MAX_SLIDES)
    @Valid
    public List<Slide> slides;

    @Valid
    public ExportOptions options = new ExportOptions();
}