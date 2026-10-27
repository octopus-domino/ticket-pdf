package ru.ticket.dto;

import java.util.List;

public class StampShapeItem {
    public String id;
    public String kind;
    public double x;
    public double y;
    public double width;
    public double height;
    public Double rotation = 0.0;
    public double cornerRadius = 0;
    public double[] points;
    public String stroke = "#000000";
    public double strokeWidth = 1;
    public String dash = "solid";
    public String fill;
    public String fillMode;
    public String fillOuterId;
    public List<String> fillHoleIds;
    public String fillTargetShapeId;
    public FillAnchorLocal fillAnchorLocal;
    public String name;
    public Boolean locked;
    public Boolean hidden;

    public static class FillAnchorLocal {
        public double x;
        public double y;
    }
}