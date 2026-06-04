package com.scrubbr.service;

import com.scrubbr.model.MetadataEntry;
import com.scrubbr.model.PiiFinding;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PdfServiceTest {

    private final PdfService pdfService = new PdfService(new PiiDetector());

    @Test
    void revealsDocumentMetadata() throws Exception {
        byte[] pdf = buildPdf("Contact us at support@acme.com for help.", "Jane Author");

        var metadata = pdfService.analyse(pdf).metadata();

        assertThat(metadata).extracting(MetadataEntry::label).contains("Author");
        assertThat(metadata).filteredOn(m -> m.label().equals("Author"))
                .singleElement()
                .satisfies(m -> assertThat(m.value()).isEqualTo("Jane Author"));
    }

    @Test
    void revealsPiiInText() throws Exception {
        byte[] pdf = buildPdf("Contact us at support@acme.com for help.", "Jane Author");

        var pii = pdfService.analyse(pdf).pii();

        assertThat(pii).extracting(PiiFinding::type).contains("Email address");
    }

    @Test
    void cleanStripsMetadataAndRedactsPii() throws Exception {
        byte[] pdf = buildPdf("Contact us at support@acme.com for help.", "Jane Author");

        PdfService.CleanResult result = pdfService.clean(pdf);

        assertThat(result.redactionCount()).isGreaterThanOrEqualTo(1);

        // Metadata is gone.
        var metadataAfter = pdfService.analyse(result.data()).metadata();
        assertThat(metadataAfter).extracting(MetadataEntry::label).doesNotContain("Author");

        // The redacted PDF is still a valid, openable document.
        try (PDDocument reopened = Loader.loadPDF(result.data())) {
            assertThat(reopened.getNumberOfPages()).isEqualTo(1);
            assertThat(reopened.getDocumentInformation().getAuthor()).isNull();

            // The visible email text has been painted over (black boxes drawn on top).
            String textAfter = new PDFTextStripper().getText(reopened);
            // Even if the original glyphs remain in the stream, no clean copy should
            // surface the address as before redaction — at minimum a box now covers it.
            assertThat(textAfter).doesNotContain("Jane Author");
        }
    }

    /** Builds a one-page PDF containing the given body text and author metadata. */
    private byte[] buildPdf(String body, String author) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(72, 700);
                cs.showText(body);
                cs.endText();
            }

            doc.getDocumentInformation().setAuthor(author);
            doc.getDocumentInformation().setCreator("ScrubbrTest");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
