package ru.ticket.routing.trip;

import java.time.Instant;
import java.time.LocalDate;

public record TripSummary(String id, String title, LocalDate startDate, LocalDate endDate, Instant updatedAt) {}
