package com.resumeagent.service;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * PDF 简历文本抽取（PDFBox）。
 * 仅抽取文本层；扫描版图片 PDF 无文本层时返回空串，由前端提示用户粘贴文本。
 */
@Component
public class PdfTextExtractor {

    public String extract(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getNumberOfPages() == 0) {
                return "";
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            return text == null ? "" : text.trim();
        }
    }
}
