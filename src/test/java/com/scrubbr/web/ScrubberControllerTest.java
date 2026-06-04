package com.scrubbr.web;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ScrubberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void analyzeReturnsReportForPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "secret.pdf", "application/pdf",
                buildPdf("Email me: agent@spy.gov"));

        mockMvc.perform(multipart("/api/analyze").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileKind").value("PDF"))
                .andExpect(jsonPath("$.canClean").value(true))
                .andExpect(jsonPath("$.pii[0].type").value("Email address"));
    }

    @Test
    void cleanReturnsADownloadableFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "secret.pdf", "application/pdf",
                buildPdf("Email me: agent@spy.gov"));

        mockMvc.perform(multipart("/api/clean").file(file))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("secret-scrubbed.pdf")))
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void cleanSanitizesDownloadFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../secret\"\r\nX-Bad: yes.pdf", "application/pdf",
                buildPdf("Email me: agent@spy.gov"));

        mockMvc.perform(multipart("/api/clean").file(file))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("secret___X-Bad_ yes-scrubbed.pdf")))
                .andExpect(header().doesNotExist("X-Bad"));
    }

    @Test
    void unsupportedFileIsReportedNotCleaned() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "just some text".getBytes());

        mockMvc.perform(multipart("/api/analyze").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileKind").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.canClean").value(false));
    }

    private byte[] buildPdf(String body) throws Exception {
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
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
