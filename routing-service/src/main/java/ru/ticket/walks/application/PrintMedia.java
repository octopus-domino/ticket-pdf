package ru.ticket.walks.application;

/** Registers an attributed walk photograph in the document service, returning its media ID. */
@FunctionalInterface
public interface PrintMedia {
    String save(String imageData);
}
