package ru.ticket.dto;

import java.util.List;

public class AppearanceFill {
    public String id;
    public String type; // solid, linear, radial
    public String color;
    public List<GradientStop> stops;
    public Double angle;
    public Double opacity;
}