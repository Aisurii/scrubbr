package com.scrubbr.model;

import java.util.List;

/** The analysis result returned to the browser. */
public record AnalysisReport(
        String fileName,
        FileKind fileKind,
        List<MetadataEntry> metadata,
        List<PiiFinding> pii,
        boolean canClean,
        String summary
) {
}
