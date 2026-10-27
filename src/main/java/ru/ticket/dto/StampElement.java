package ru.ticket.dto;

import java.util.ArrayList;
import java.util.List;

public class StampElement extends Element {

    // Новый формат
    public List<StampShapeItem> shapes = new ArrayList<>();
    public List<StampTextItem> texts = new ArrayList<>();
    public List<StampImageItem> images = new ArrayList<>();
    public String libraryId;

    // Legacy-формат (frontend StampEl с shape/stampColor/…).
    // Рендерится, только если shapes пуст И shape != null.
    public String shape;            // "circle" | "oval" | "rect" | "triangle"
    public String stampColor;       // основной цвет обводки/текста
    public Double strokeWidth;
    public String strokeDash;       // "solid" | "dashed" | "dotted" | "dashdot" | "longdash"
    public Double innerStrokeWidth;
    public String innerStrokeDash;
    public Boolean showInner;

    public String topText;
    public Double topFontSize;
    public String topFontFamily;
    public String topFontStyle;
    public String topTextDecoration;
    public String topColor;

    public String centerText;
    public Double centerFontSize;
    public String centerFontFamily;
    public String centerFontStyle;
    public String centerTextDecoration;
    public String centerColor;

    public String bottomText;
    public Double bottomFontSize;
    public String bottomFontFamily;
    public String bottomFontStyle;
    public String bottomTextDecoration;
    public String bottomColor;
}