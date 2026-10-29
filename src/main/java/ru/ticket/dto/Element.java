package ru.ticket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextElement.class,        name = "text"),
        @JsonSubTypes.Type(value = ShapeElement.class,       name = "shape"),
        @JsonSubTypes.Type(value = LineElement.class,        name = "line"),
        @JsonSubTypes.Type(value = ImageElement.class,       name = "image"),
        @JsonSubTypes.Type(value = StampElement.class,       name = "stamp"),
        @JsonSubTypes.Type(value = TableElement.class,       name = "table"),
        @JsonSubTypes.Type(value = FooterElement.class,      name = "footer"),
        @JsonSubTypes.Type(value = PlaceholderElement.class, name = "placeholder"),
        @JsonSubTypes.Type(value = FillElement.class,        name = "fill"),
        @JsonSubTypes.Type(value = GroupElement.class,       name = "group"),
        @JsonSubTypes.Type(value = PathElement.class,        name = "path"),
        @JsonSubTypes.Type(value = ChartElement.class,       name = "chart"),
        @JsonSubTypes.Type(value = ConnectorElement.class,   name = "connector"),
        @JsonSubTypes.Type(value = DiagramElement.class,     name = "diagram"),
        @JsonSubTypes.Type(value = QrElement.class,          name = "qr"),
})
public abstract class Element {
    public String id;
    public String type;
    public double x;
    public double y;
    public double width;
    public double height;
    public double rotation;
    public Boolean locked;
    public Appearance appearance;
    public String clipPathId;

    public Boolean flipX;
    public Boolean flipY;

    public transient double origX = Double.NaN;
    public transient double origY = Double.NaN;

    public double originalX() { return Double.isNaN(origX) ? x : origX; }
    public double originalY() { return Double.isNaN(origY) ? y : origY; }
}