package ru.ticket.routing.domain;

import java.util.List;

/** Provider geometry in longitude, latitude order. */
public record Road(List<List<Double>> coordinates, double distanceMeters, double durationSeconds) {}
