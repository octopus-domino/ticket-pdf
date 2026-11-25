package ru.ticket.generation.application.port;

import java.util.List;
import java.util.Optional;

public interface JobStore<T> {
    T create(T job);
    Optional<T> find(String jobId);
    T update(T job);
    void delete(String jobId);
    List<T> findByUser(String userId);
    List<T> findAll();
}