package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.report.CaseReview;
import org.di.digital.service.report.ReportService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static java.net.URLEncoder.encode;

@Slf4j
@RestController
@RequestMapping("/cases/{caseNumber}/report")
@RequiredArgsConstructor
public class CaseReportController {

    private final ReportService reportService;

    @PostMapping("/generate")
    public ResponseEntity<Resource> generate(@PathVariable String caseNumber,
                                             Authentication authentication) {
        log.info("Generating report for case: {} by user: {}",
                caseNumber, authentication.getName());

        Resource resource = reportService.generateReport(caseNumber, authentication.getName());

        String filename = String.format("отчёт_%s.docx", caseNumber.replace("/", "-"));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encode(filename, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(resource);
    }

    @GetMapping
    public ResponseEntity<CaseReview> getReport(@PathVariable String caseNumber) {
        return ResponseEntity.ok(reportService.getByCaseNumber(caseNumber));
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(@PathVariable String caseNumber,
                                             Authentication authentication) {
        log.info("Downloading report for case: {} by user: {}",
                caseNumber, authentication.getName());

        Resource resource = reportService.downloadReport(caseNumber, authentication.getName());

        String filename = String.format("отчёт_%s.docx", caseNumber.replace("/", "-"));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encode(filename, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(resource);
    }
}