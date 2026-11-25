package ru.ticket.dto;

public class ConnectorElement extends Element {
    public String fromId;
    public String fromAnchor;
    public String toId;
    public String toAnchor;
    public String stroke;
    public double strokeWidth;
    public String dash;
    public boolean isArrow;
    public String routing; // orthogonal, straight
    public String label;
}