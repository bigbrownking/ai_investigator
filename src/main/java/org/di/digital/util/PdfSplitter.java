package org.di.digital.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class PdfSplitter {

    public byte[] mergeSegments(List<byte[]> pdfBytesList) throws IOException {
        PDFMergerUtility merger = new PDFMergerUtility();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        merger.setDestinationStream(out);

        for (byte[] pdfBytes : pdfBytesList) {
            merger.addSource(new RandomAccessReadBuffer(pdfBytes));
        }
        merger.mergeDocuments(null);

        return out.toByteArray();
    }

    public byte[] extractPages(byte[] pdfBytes, int startPage, int endPage) throws IOException {
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes))) {
            int totalPages = doc.getNumberOfPages();
            int from = Math.max(1, startPage);
            int to = Math.min(totalPages, endPage);

            if (from > to) {
                log.warn("Invalid page range: startPage={}, endPage={}, totalPages={}",
                        startPage, endPage, totalPages);
                return new byte[0];
            }

            for (int i = totalPages - 1; i >= to; i--) {
                doc.removePage(i);
            }

            for (int i = from - 2; i >= 0; i--) {
                doc.removePage(i);
            }

            log.info("Extracted pages {}-{} from {} total pages", from, to, totalPages);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}