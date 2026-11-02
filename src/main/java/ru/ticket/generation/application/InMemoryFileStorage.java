package ru.ticket.generation.application;

import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryFileStorage implements JobFileStorage {

    private record Entry(String filename, byte[] content) { }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public String save(String jobId, String filename, byte[] content) {
        store.put(jobId, new Entry(filename, content));
        return jobId;
    }

    @Override
    public byte[] read(String jobId) {
        Entry e = store.get(jobId);
        if (e == null) throw ApiException.notFound("Job file");
        return e.content();
    }

    @Override
    public String filename(String jobId) {
        Entry e = store.get(jobId);
        return e == null ? "documents.zip" : e.filename();
    }

    @Override
    public void delete(String jobId) {
        store.remove(jobId);
    }
}