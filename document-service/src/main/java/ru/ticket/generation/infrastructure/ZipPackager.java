package ru.ticket.generation.infrastructure;

import org.springframework.stereotype.Component;
import ru.ticket.generation.application.dto.NamedDocument;
import ru.ticket.generation.application.dto.PackResult;
import ru.ticket.generation.application.port.DocumentPackager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class ZipPackager implements DocumentPackager {

    @Override
    public String packAs() { return "zip"; }

    @Override
    public PackResult pack(List<NamedDocument> documents) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Set<String> used = new HashSet<>();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (NamedDocument doc : documents) {
                String name = unique(doc.filename(), used);
                ZipEntry entry = new ZipEntry(name);
                zos.putNextEntry(entry);
                zos.write(doc.content());
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException("ZIP packaging failed: " + e.getMessage(), e);
        }
        return new PackResult(out.toByteArray(), "documents.zip", "application/zip");
    }

    private String unique(String name, Set<String> used) {
        String candidate = name;
        int i = 1;
        while (!used.add(candidate)) {
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String ext  = dot > 0 ? name.substring(dot) : "";
            candidate = base + "-" + i + ext;
            i++;
        }
        return candidate;
    }

    @Override
    public String mimeType() { return "application/zip"; }

    @Override
    public String extension() { return ".zip"; }
}