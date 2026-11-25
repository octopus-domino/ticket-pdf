package ru.ticket.walks.domain.model;

import java.util.Map;

/** A printable media reference and its required attribution. */
public record WalkPhoto(String stopId, String imageId, String sourceUrl, String author, String license,
                        String licenseUrl) {
    public record Stop(String id, String label, double longitude, double latitude, Map<String, String> meta) {}
}
