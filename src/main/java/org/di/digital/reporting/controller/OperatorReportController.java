package org.di.digital.reporting.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.di.digital.reporting.dto.request.ReportRowRequest;
import org.di.digital.reporting.dto.response.ActiveFormResponse;
import org.di.digital.reporting.dto.response.OperatorFormResponse;
import org.di.digital.reporting.service.SubmissionService;
import org.di.digital.reporting.service.TemplateService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Regional reports are filled in by zonal users (ZONAL, one per region); the region comes from
 * the current user, never from the request.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reporting/operator/templates")
@PreAuthorize("hasAuthority('ZONAL')")
public class OperatorReportController {

    private final TemplateService templateService;
    private final SubmissionService submissionService;

    @GetMapping
    public ResponseEntity<List<ActiveFormResponse>> listTemplates() {
        return ResponseEntity.ok(templateService.listActiveForms());
    }

    /** @param date yyyy-MM-dd, today when omitted */
    @GetMapping("/{templateCode}")
    public ResponseEntity<OperatorFormResponse> getForm(
            @PathVariable String templateCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(submissionService.getForm(templateCode, date));
    }

    @PostMapping("/{templateCode}/draft")
    public ResponseEntity<OperatorFormResponse> saveDraft(
            @PathVariable String templateCode,
            @Valid @RequestBody ReportRowRequest request) {
        return ResponseEntity.ok(submissionService.saveDraft(templateCode, request));
    }

    @PostMapping("/{templateCode}/submit")
    public ResponseEntity<OperatorFormResponse> submit(
            @PathVariable String templateCode,
            @Valid @RequestBody ReportRowRequest request) {
        return ResponseEntity.ok(submissionService.submit(templateCode, request));
    }
}
