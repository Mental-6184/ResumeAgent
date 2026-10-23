package com.resumeagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

/**
 * PDF 简历文本抽取单测：用 PDFBox 生成带文本层的 PDF 后抽取验证。
 */
class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    void shouldExtractTextFromPdfWithTextLayer() throws IOException {
        byte[] pdf = buildPdf("Zhang San Resume. Java Spring Boot experience.");

        String text = extractor.extract(pdf);

        assertThat(text).contains("Zhang San");
        assertThat(text).contains("Spring Boot");
    }

    @Test
    void shouldReturnEmptyForBlankPdf() throws IOException {
        byte[] pdf = buildPdf("");

        assertThat(extractor.extract(pdf)).isEmpty();
    }

    private byte[] buildPdf(String content) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (!content.isBlank()) {
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(100, 700);
                    stream.showText(content);
                    stream.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
