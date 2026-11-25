package ru.ticket.dto;

import java.util.List;

public class ChartElement extends Element {
    public String chartKind;        // "bar" | "donut"
    public String dataPath;         // для прозрачности схемы
    public String valueField;       // для прозрачности схемы
    public List<Double> values;
    public List<String> palette;
    public double barWidth = 40;
    public double gap = 12;
    public double donutInnerRatio = 0.5;
}