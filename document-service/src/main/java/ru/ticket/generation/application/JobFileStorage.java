package ru.ticket.generation.application;

public interface JobFileStorage {
    String save(String jobId, String filename, byte[] content);
    byte[] read(String jobId);
    String filename(String jobId);
    void delete(String jobId);
}