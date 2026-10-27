package ru.ticket.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class Slide {
    public String id;
    public String name = "page";
    public String format = "custom";
    public String orientation = "portrait";

    @Positive(message = "slide.width must be > 0")
    public double width = 794;

    @Positive(message = "slide.height must be > 0")
    public double height = 1123;

    public String background = "#ffffff";
    public String backgroundImage;
    public Padding padding = new Padding();

    @NotNull
    @Size(max = ExportRequest.MAX_ELEMENTS_PER_SLIDE,
            message = "too many elements on slide")
    @Valid
    public List<Element> elements = new ArrayList<>();
}