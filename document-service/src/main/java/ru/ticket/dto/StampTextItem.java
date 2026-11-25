package ru.ticket.dto;

public class StampTextItem {
    public String id;
    public String text = "";
    public String mode = "straight";

    public double x;
    public double y;
    public double width = 200;
    public String align = "center";

    public double arcCx;
    public double arcCy;
    public double arcRadius = 60;
    public Double arcRx;
    public Double arcRy;
    public double arcStartDeg = -90;
    public double arcEndDeg = 90;
    public boolean arcFlip = false;
    public String arcHostId;
    public Boolean arcOutside;
    public Double arcOffset = 6.0;
    public Double arcSpanDeg = 140.0;

    public double fontSize = 12;
    public String fontFamily = "Arial";
    public String fontStyle = "normal";
    public String textDecoration = "";
    public String fill = "#000000";

    public String name;
    public Boolean locked;
    public Boolean hidden;
}