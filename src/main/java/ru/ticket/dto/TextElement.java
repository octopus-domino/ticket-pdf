package ru.ticket.dto;

public class TextElement extends Element {
    public String text = "";
    public String html;
    public Boolean richText;
    public double fontSize = 14;
    public String fontFamily = "Arial";
    public String fill = "#000000";
    public String fontStyle = "normal";
    public String textDecoration = "";
    public String align = "left";
    public String verticalAlign = "top";
    public double lineHeight = 1.5;
    public Padding padding = new Padding();
    public String background = "transparent";

    // arc-режим (совместимость с frontend TextElement)
    public String textMode = "straight"; // "straight" | "arc"
    public Double arcRadius;
    public Double arcStartAngle;
    public Double arcEndAngle;
    public Boolean arcFlip;
}