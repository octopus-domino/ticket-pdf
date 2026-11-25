package ru.ticket.dto;

public class PlaceholderElement extends Element {
    public String fieldPath = "";
    public String fieldType = "string";
    public String sample = "";
    public String label = "";
    public Boolean inline;
    public double fontSize = 14;
    public String fontFamily = "Arial";
    public String fill = "#000000";
    public String fontStyle = "normal";
    public String textDecoration = "";
    public String align = "left";
    public String verticalAlign = "top";
    public double lineHeight = 1.2;
    public Padding padding = new Padding();
    public String background = "transparent";
}