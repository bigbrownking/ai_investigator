package org.di.digital.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.di.digital.service.MinioService;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

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
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                        countByConvertingToPdf(is.readAllBytes(), "docx");
                case "application/msword" ->
                        countByConvertingToPdf(is.readAllBytes(), "doc");
                case "text/plain" -> countTxtPages(is);
                default -> null;
            };
        } catch (Exception e) {
            log.warn("Could not count pages for url {}: {}", fileUrl, e.getMessage());
            return null;
        }
    }

    private Integer countByConvertingToPdf(byte[] bytes, String extension) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("pagecounter_");
            Path inputFile = tempDir.resolve("input." + extension);
            Files.write(inputFile, bytes);

            ProcessBuilder pb = new ProcessBuilder(
                    "libreoffice", "--headless", "--convert-to", "pdf",
                    "--outdir", tempDir.toString(),
                    inputFile.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (InputStream stdout = process.getInputStream()) {
                String output = new String(stdout.readAllBytes());
                log.debug("LibreOffice output: {}", output);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("LibreOffice exited with code {} for file.{}", exitCode, extension);
                return fallbackCount(bytes, extension);
            }

            Path pdfFile = tempDir.resolve("input.pdf");
            if (!Files.exists(pdfFile)) {
                log.warn("LibreOffice did not produce PDF for file.{}", extension);
                return fallbackCount(bytes, extension);
            }

            try (PDDocument doc = Loader.loadPDF(Files.readAllBytes(pdfFile))) {
                return doc.getNumberOfPages();
            }

        } catch (Exception e) {
            log.warn("LibreOffice conversion failed for .{}: {}", extension, e.getMessage());
            return fallbackCount(bytes, extension);
        } finally {
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                            .sorted((a, b) -> -a.compareTo(b))
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                            });
                } catch (Exception ignored) {}
            }
        }
    }

    private Integer fallbackCount(byte[] bytes, String extension) {
        try {
            if ("docx".equals(extension)) {
                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                    Integer pages = doc.getProperties()
                            .getExtendedProperties()
                            .getUnderlyingProperties()
                            .getPages();
                    if (pages != null && pages > 0) return pages;
                }
            } else if ("doc".equals(extension)) {
                try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(bytes))) {
                    int pages = doc.getSummaryInformation().getPageCount();
                    if (pages > 0) return pages;
                }
            }

            Metadata metadata = new Metadata();
            new AutoDetectParser().parse(
                    new ByteArrayInputStream(bytes),
                    new BodyContentHandler(-1), metadata);
            String p = metadata.get("meta:page-count");
            if (p != null && Integer.parseInt(p) > 0) return Integer.parseInt(p);

        } catch (Exception e) {
            log.warn("Fallback page count failed: {}", e.getMessage());
        }
        return null;
    }

    private Integer countPdfPages(InputStream is) throws Exception {
        try (PDDocument doc = Loader.loadPDF(is.readAllBytes())) {
            return doc.getNumberOfPages();
        }
    }

    private Integer countTxtPages(InputStream is) throws Exception {
        String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        int charsPerPage = 3000;
        return (int) Math.ceil((double) text.length() / charsPerPage);
    }
}