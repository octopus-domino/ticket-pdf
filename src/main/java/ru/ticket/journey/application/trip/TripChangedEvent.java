package ru.ticket.journey.application.trip;

/** Signals that the account's persisted trip snapshot changed. */
public record TripChangedEvent(String tripId) {}
