package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.response.zonal.ExportFile;
import org.di.digital.dto.response.zonal.ZonalConsolidatedResponse;
import org.di.digital.dto.response.zonal.ZonalSubmissionResponse;
import org.di.digital.dto.response.zonal.ZonalSubmissionSummaryResponse;
import org.di.digital.security.zonal.ReportingAccess;
import org.di.digital.service.zonal.ZonalReportAdminService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/zonal")
@PreAuthorize(ReportingAccess.ADMIN_OR_AFM_REG_ADMIN)
public class ZonalReportAdminController {

    private final ZonalReportAdminService reportService;

    @GetMapping("/submissions")
    public ResponseEntity<Page<ZonalSubmissionSummaryResponse>> getHistory(
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reportService.getHistory(templateId, regionId, from, to, page, size));
    }

    @GetMapping("/submissions/{id}")
    public ResponseEntity<ZonalSubmissionResponse> getSubmission(@PathVariable Long id) {
        return ResponseEntity.ok(reportService.getSubmission(id));
    }

    @GetMapping("/templates/{templateId}/consolidated")
    public ResponseEntity<ZonalConsolidatedResponse> getConsolidated(
            @PathVariable Long templateId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reportService.getConsolidated(templateId, date));
    }

    @GetMapping("/templates/{templateId}/consolidated/export")
    public ResponseEntity<byte[]> exportConsolidated(
            @PathVariable Long templateId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ExportFile file = reportService.exportConsolidated(templateId, date);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.content().length)
                .body(file.content());
    }
}
