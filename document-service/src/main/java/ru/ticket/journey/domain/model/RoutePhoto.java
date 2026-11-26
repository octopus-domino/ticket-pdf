package ru.ticket.journey.domain.model;

/** Media stored by CoDoc, paired with the point and the rights required for publication. */
public record RoutePhoto(String stopId, String imageId, String sourceUrl, String author, String license, String licenseUrl) {
  public boolean printable() {
    return imageId != null && imageId.matches("[a-f0-9]{64}")
        && sourceUrl != null && sourceUrl.matches("https://commons\\.wikimedia\\.org/wiki/File:[^\\p{Cntrl}<>\" #]{1,1500}")
        && author != null && !author.isBlank() && author.length() <= 500
        && license != null && !license.isBlank() && license.length() <= 80
        && licenseUrl != null && licenseUrl.matches("https://creativecommons\\.org/(?:licenses/(?:by|by-sa)/[1-4]\\.[0-9]|publicdomain/(?:zero|mark)/1\\.0)/(?:deed\\.[a-z-]+)?");
  }
}
