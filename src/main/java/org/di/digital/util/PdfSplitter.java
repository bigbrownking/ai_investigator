package org.di.digital.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
public class PdfSplitter {

    public byte[] extractPages(byte[] pdfBytes, int startPage, int endPage) throws IOException {
        try (PDDocument source = Loader.loadPDF(pdfBytes);
             PDDocument target = new PDDocument()) {

            int totalPages = source.getNumberOfPages();
            int from = Math.max(1, startPage);
            int to = Math.min(totalPages, endPage);

            for (int i = from; i <= to; i++) {
                PDPage page = source.getPage(i - 1);
                target.addPage(page);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            target.save(out);
            return out.toByteArray();
        }
    }

    public byte[] readAllBytes(InputStream stream) throws IOException {
        return stream.readAllBytes();
    }

    public byte[] mergeSegments(List<byte[]> pdfBytesList) throws IOException {
        try (PDDocument merged = new PDDocument()) {
            for (byte[] pdfBytes : pdfBytesList) {
                try (PDDocument source = Loader.loadPDF(pdfBytes)) {
                    for (PDPage page : source.getPages()) {
                        merged.addPage(page);
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            merged.save(out);
            return out.toByteArray();
        }
    }
}