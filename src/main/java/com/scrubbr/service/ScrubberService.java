package com.scrubbr.service;

import com.scrubbr.model.AnalysisReport;
import com.scrubbr.model.FileKind;
import com.scrubbr.model.MetadataEntry;
import com.scrubbr.model.PiiFinding;
import com.scrubbr.model.Severity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/** Detects the file type and routes it to the right service for analysis and cleaning. */
@Service
public class ScrubberService {

    private final FileTypeDetector fileTypeDetector;
    private final ImageService imageService;
    private final PdfService pdfService;

    public ScrubberService(FileTypeDetector fileTypeDetector,
                           ImageService imageService,
                           PdfService pdfService) {
        this.fileTypeDetector = fileTypeDetector;
        this.imageService = imageService;
        this.pdfService = pdfService;
    }

    public AnalysisReport analyse(String fileName, byte[] data) throws IOException {
        FileKind kind = fileTypeDetector.detect(data);
        List<MetadataEntry> metadata;
        List<PiiFinding> pii = List.of();

        switch (kind) {
            case JPEG, PNG -> metadata = imageService.reveal(data);
            case PDF -> {
                PdfService.PdfAnalysis analysis = pdfService.analyse(data);
                metadata = analysis.metadata();
                pii = analysis.pii();
            }
            default -> {
                return new AnalysisReport(fileName, kind, List.of(), List.of(), false,
                        "Unsupported file type. Scrubbr currently handles JPEG, PNG and PDF.");
            }
        }
        return new AnalysisReport(fileName, kind, metadata, pii, true, summarise(metadata, pii));
    }

    public record CleanedFile(byte[] data, String fileName, String contentType) {
    }

    public CleanedFile clean(String fileName, byte[] data) throws IOException {
        FileKind kind = fileTypeDetector.detect(data);
        return switch (kind) {
            case JPEG -> new CleanedFile(imageService.strip(data, kind),
                    cleanName(fileName, "jpg"), "image/jpeg");
            case PNG -> new CleanedFile(imageService.strip(data, kind),
                    cleanName(fileName, "png"), "image/png");
            case PDF -> new CleanedFile(pdfService.clean(data).data(),
                    cleanName(fileName, "pdf"), "application/pdf");
            default -> throw new IllegalArgumentException(
                    "Unsupported file type; cannot clean this file.");
        };
    }

    private String summarise(List<MetadataEntry> metadata, List<PiiFinding> pii) {
        long highMeta = metadata.stream().filter(m -> m.severity() == Severity.HIGH).count();
        int piiCount = pii.stream().mapToInt(PiiFinding::count).sum();
        if (metadata.isEmpty() && pii.isEmpty()) {
            return "Looks clean — no hidden metadata or sensitive data found.";
        }
        StringBuilder sb = new StringBuilder("Found ");
        sb.append(metadata.size()).append(metadata.size() == 1 ? " metadata item" : " metadata items");
        if (highMeta > 0) {
            sb.append(" (").append(highMeta).append(" high-risk)");
        }
        if (piiCount > 0) {
            sb.append(" and ").append(piiCount)
                    .append(piiCount == 1 ? " piece" : " pieces").append(" of sensitive data");
        }
        return sb.append('.').toString();
    }

    private String cleanName(String original, String extension) {
        String base = (original == null || original.isBlank()) ? "file" : original;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base + "-scrubbed." + extension;
    }
}
