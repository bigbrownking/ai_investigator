package org.di.digital.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.di.digital.model.CaseFile;
import org.di.digital.service.MinioService;
import org.springframework.stereotype.Service;
import org.xml.sax.ContentHandler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageCounter {
    private final MinioService minioService;

    public Integer countPagesByUrl(String fileUrl, String contentType) {
        try (InputStream is = minioService.downloadFile(fileUrl)) {
            if (contentType == null) return null;
            return switch (contentType) {
                case "application/pdf" -> countPdfPages(is);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> countDocxPages(is);
                case "application/msword" -> countDocPages(is);
                case "text/plain" -> countTxtPages(is);
                default -> null;
            };
        } catch (Exception e) {
            log.warn("Could not count pages for url {}: {}", fileUrl, e.getMessage());
            return null;
        }
    }

    private Integer countPdfPages(InputStream is) throws Exception {
        try (PDDocument doc = Loader.loadPDF(is.readAllBytes())) {
            return doc.getNumberOfPages();
        }
    }

    private Integer countDocxPages(InputStream is) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(is)) {
            Integer pages = doc.getProperties()
                    .getExtendedProperties()
                    .getUnderlyingProperties()
                    .getPages();
            return (pages != null && pages > 0) ? pages : null;
        }
    }

    private Integer countDocPages(InputStream is) throws Exception {
        Metadata metadata = new Metadata();
        AutoDetectParser parser = new AutoDetectParser();
        ContentHandler handler = new BodyContentHandler(-1);
        parser.parse(is, handler, metadata);

        String pages = metadata.get("meta:page-count");
        if (pages != null) {
            return Integer.parseInt(pages);
        }
        return null;
    }
    private Integer countTxtPages(InputStream is) throws Exception {
        String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        int charsPerPage = 3000;
        return (int) Math.ceil((double) text.length() / charsPerPage);
    }

}
