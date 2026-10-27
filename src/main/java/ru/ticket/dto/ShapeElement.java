package ru.ticket.dto;

import java.util.List;

public class ShapeElement extends Element {
    public String shape = "rect";
    public String fill = "transparent";
    public String stroke = "#000000";
    public double strokeWidth = 1;

    public String text = "";
    public double fontSize = 16;
    public String textFill = "#111";
    public Double radius = 0.0;
    public String textAlign = "center";
    public String textVerticalAlign = "middle";
    public Padding textPadding = new Padding();
    public String textFontFamily = "Arial";
    public double textLineHeight = 1.3;
    public String textFontStyle = "normal";
    public String textDecoration = "";

    // Pathfinder / polygon
    public List<Double> polygonPoints;
    public List<List<Double>> polygonSubpaths;

    // Arc
    public Double arcStartAngle;
    public Double arcEndAngle;
    public Double arcInnerRadius;
    public Double arcOuterRadius;
}