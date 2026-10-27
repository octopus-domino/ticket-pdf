package ru.ticket.dto;

import java.util.List;

public class TableElement extends Element {
    public int rows = 3;
    public int cols = 3;
    public List<List<String>> data;
    public String borderColor = "#cbd5e1";
    public String headerBg = "#e2e8f0";
    public double fontSize = 13;
}