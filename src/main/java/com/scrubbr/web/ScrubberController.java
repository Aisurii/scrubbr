package com.scrubbr.web;

import com.scrubbr.model.AnalysisReport;
import com.scrubbr.service.ScrubberService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** HTTP API: {@code POST /api/analyze} reports findings, {@code POST /api/clean} returns the cleaned file. */
@RestController
@RequestMapping("/api")
public class ScrubberController {

    private final ScrubberService scrubberService;

    public ScrubberController(ScrubberService scrubberService) {
        this.scrubberService = scrubberService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalysisReport analyze(@RequestParam("file") MultipartFile file) throws IOException {
        requireNonEmpty(file);
        return scrubberService.analyse(file.getOriginalFilename(), file.getBytes());
    }

    @PostMapping(value = "/clean", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> clean(@RequestParam("file") MultipartFile file) throws IOException {
        requireNonEmpty(file);
        ScrubberService.CleanedFile cleaned =
                scrubberService.clean(file.getOriginalFilename(), file.getBytes());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + cleaned.fileName() + "\"")
                .contentType(MediaType.parseMediaType(cleaned.contentType()))
                .body(cleaned.data());
    }

    private void requireNonEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
    }
}
