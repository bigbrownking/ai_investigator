package org.di.digital.reporting.controller;

import lombok.RequiredArgsConstructor;
import org.di.digital.reporting.dto.response.ConsolidatedTableResponse;
import org.di.digital.reporting.service.ConsolidationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Consolidated tables for main admins (ADMIN authority). "Refresh" repeats the GET.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reporting/admin/consolidated")
@PreAuthorize("hasAuthority('ADMIN')")
public class ConsolidatedReportController {

    private final ConsolidationService consolidationService;

    /** @param date yyyy-MM-dd, today when omitted */
    @GetMapping("/{templateCode}")
    public ResponseEntity<ConsolidatedTableResponse> getConsolidated(
            @PathVariable String templateCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(consolidationService.getConsolidated(templateCode, date));
    }
}
