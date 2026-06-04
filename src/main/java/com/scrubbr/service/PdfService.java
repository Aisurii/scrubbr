package com.scrubbr.service;

import com.scrubbr.model.MetadataEntry;
import com.scrubbr.model.PiiFinding;
import com.scrubbr.model.Severity;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Reveals and removes hidden metadata and PII in PDF files. */
@Service
public class PdfService {

    private static final float RASTER_DPI = 200f;
    private static final float JPEG_QUALITY = 0.92f;

    private final PiiDetector piiDetector;

    public PdfService(PiiDetector piiDetector) {
        this.piiDetector = piiDetector;
    }

    public record PdfAnalysis(List<MetadataEntry> metadata, List<PiiFinding> pii) {
    }

    public record CleanResult(byte[] data, int redactionCount) {
    }

    /** Reads metadata and PII in a single pass over the document. */
    public PdfAnalysis analyse(byte[] data) throws IOException {
        try (PDDocument doc = Loader.loadPDF(data)) {
            return new PdfAnalysis(extractMetadata(doc), extractPii(doc));
        }
    }

    /** Wipes metadata and returns a flattened copy where redacted text is no longer extractable. */
    public CleanResult clean(byte[] data) throws IOException {
        try (PDDocument doc = Loader.loadPDF(data)) {
            CapturingStripper stripper = new CapturingStripper();
            stripper.setSortByPosition(true);
            stripper.getText(doc);

            int redactions = 0;
            for (PageText page : stripper.pages) {
                var matches = piiDetector.findMatches(page.text.toString());
                if (matches.isEmpty()) {
                    continue;
                }
                PDPage pdPage = doc.getPage(page.pageNumber - 1);
                float pageHeight = pdPage.getMediaBox().getHeight();
                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, pdPage, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setNonStrokingColor(Color.BLACK);
                    for (PiiDetector.Match match : matches) {
                        redactions++;
                        for (int i = match.start(); i < match.end() && i < page.positions.size(); i++) {
                            TextPosition tp = page.positions.get(i);
                            if (tp != null) {
                                paintBox(cs, tp, pageHeight);
                            }
                        }
                    }
                }
            }

            doc.setDocumentInformation(new PDDocumentInformation());
            doc.getDocumentCatalog().setMetadata(null);

            return new CleanResult(flatten(doc), redactions);
        }
    }

    private byte[] flatten(PDDocument source) throws IOException {
        PDFRenderer renderer = new PDFRenderer(source);
        try (PDDocument target = new PDDocument()) {
            target.setDocumentInformation(new PDDocumentInformation());
            target.getDocumentCatalog().setMetadata(null);

            for (int pageIndex = 0; pageIndex < source.getNumberOfPages(); pageIndex++) {
                PDPage sourcePage = source.getPage(pageIndex);
                PDRectangle mediaBox = sourcePage.getMediaBox();
                PDPage targetPage = new PDPage(new PDRectangle(mediaBox.getWidth(), mediaBox.getHeight()));
                target.addPage(targetPage);

                BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, RASTER_DPI, ImageType.RGB);
                PDImageXObject image = JPEGFactory.createFromImage(target, pageImage, JPEG_QUALITY);
                try (PDPageContentStream cs = new PDPageContentStream(target, targetPage)) {
                    cs.drawImage(image, 0, 0, mediaBox.getWidth(), mediaBox.getHeight());
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            target.save(out);
            return out.toByteArray();
        }
    }

    private List<MetadataEntry> extractMetadata(PDDocument doc) {
        List<MetadataEntry> entries = new ArrayList<>();
        PDDocumentInformation info = doc.getDocumentInformation();
        add(entries, "Author", info.getAuthor(), Severity.HIGH);
        add(entries, "Title", info.getTitle(), Severity.MEDIUM);
        add(entries, "Subject", info.getSubject(), Severity.MEDIUM);
        add(entries, "Keywords", info.getKeywords(), Severity.MEDIUM);
        add(entries, "Creator (application)", info.getCreator(), Severity.MEDIUM);
        add(entries, "Producer (library)", info.getProducer(), Severity.MEDIUM);
        add(entries, "Created", formatDate(info.getCreationDate()), Severity.HIGH);
        add(entries, "Last modified", formatDate(info.getModificationDate()), Severity.HIGH);
        if (doc.getDocumentCatalog().getMetadata() != null) {
            entries.add(new MetadataEntry("PDF", "XMP metadata packet",
                    "Present (extended metadata embedded)", Severity.MEDIUM));
        }
        return entries;
    }

    private List<PiiFinding> extractPii(PDDocument doc) throws IOException {
        List<PiiFinding> findings = new ArrayList<>();
        CapturingStripper stripper = new CapturingStripper();
        stripper.setSortByPosition(true);
        stripper.getText(doc);
        for (PageText page : stripper.pages) {
            var matches = piiDetector.findMatches(page.text.toString());
            findings.addAll(piiDetector.summarise(matches, page.pageNumber));
        }
        return findings;
    }

    private void paintBox(PDPageContentStream cs, TextPosition tp, float pageHeight) throws IOException {
        float x = tp.getXDirAdj();
        float w = tp.getWidthDirAdj();
        float h = tp.getHeightDir();
        float topFromBottom = pageHeight - tp.getYDirAdj();
        float pad = Math.max(1f, h * 0.15f);
        cs.addRect(x - pad, topFromBottom - h - pad, w + 2 * pad, h + 2 * pad);
        cs.fill();
    }

    private void add(List<MetadataEntry> entries, String label, String value, Severity severity) {
        if (value != null && !value.isBlank()) {
            entries.add(new MetadataEntry("PDF Document Info", label, value, severity));
        }
    }

    private String formatDate(Calendar calendar) {
        return calendar == null ? null : calendar.toInstant().toString();
    }

    private static final class PageText {
        final int pageNumber;
        final StringBuilder text = new StringBuilder();
        final List<TextPosition> positions = new ArrayList<>(); // parallel to text; null = separator

        PageText(int pageNumber) {
            this.pageNumber = pageNumber;
        }
    }

    /** Records the page coordinates of each glyph so a text match maps back to a rectangle. */
    private static final class CapturingStripper extends PDFTextStripper {
        final List<PageText> pages = new ArrayList<>();
        private PageText current;

        CapturingStripper() throws IOException {
            super();
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            current = new PageText(getCurrentPageNo());
            super.startPage(page);
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            pages.add(current);
            super.endPage(page);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            for (TextPosition tp : textPositions) {
                String unicode = tp.getUnicode();
                if (unicode == null || unicode.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < unicode.length(); i++) {
                    current.text.append(unicode.charAt(i));
                    current.positions.add(tp);
                }
            }
        }

        @Override
        protected void writeWordSeparator() {
            current.text.append(' ');
            current.positions.add(null);
        }

        @Override
        protected void writeLineSeparator() {
            current.text.append('\n');
            current.positions.add(null);
        }
    }
}
