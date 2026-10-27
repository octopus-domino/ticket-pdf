package ru.ticket.controller;

import jakarta.validation.Valid;
import ru.ticket.common.ApiException;
import ru.ticket.dto.ExportRequest;
import ru.ticket.export.ExportFacade;
import ru.ticket.export.ExportFormat;
import ru.ticket.export.ExportResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportFacade facade;

    public ExportController(ExportFacade facade) {
        this.facade = facade;
    }

    @PostMapping(value = "/svg", produces = "image/svg+xml")
    public ResponseEntity<byte[]> exportSvg(@Valid @RequestBody ExportRequest req) {
        if (req.slides == null || req.slides.isEmpty()) {
            throw ApiException.badRequest("slides must not be empty");
        }
        ExportResult result = facade.export(ExportFormat.SVG, req);
        return ResponseEntity.ok()
                .headers(responseHeaders(result))
                .contentType(MediaType.valueOf("image/svg+xml"))
                .body(result.content());
    }

    @PostMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf(
            @Valid @RequestBody ExportRequest req,
            @RequestParam(name = "mode", defaultValue = "pdfa") String mode) {

        if (req.slides == null || req.slides.isEmpty()) {
            throw ApiException.badRequest("slides must not be empty");
        }

        ExportFormat format = ExportFormat.fromString(mode);
        ExportResult result = facade.export(format, req);

        return ResponseEntity.ok()
                .headers(responseHeaders(result))
                .contentType(MediaType.APPLICATION_PDF)
                .body(result.content());
    }

    private HttpHeaders responseHeaders(ExportResult result) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, result.filename());
        return headers;
    }
}