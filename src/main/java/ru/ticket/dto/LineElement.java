package ru.ticket.dto;

public class LineElement extends Element {
    public double[] points;
    public String stroke = "#000000";
    public double strokeWidth = 2;
    public String dash = "solid";
    public boolean isArrow = false;
}