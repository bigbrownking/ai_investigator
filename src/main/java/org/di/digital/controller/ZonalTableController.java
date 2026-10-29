package org.di.digital.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.di.digital.dto.request.zonal.ZonalRowsRequest;
import org.di.digital.dto.response.zonal.ZonalFormResponse;
import org.di.digital.dto.response.zonal.ZonalFormSummaryResponse;
import org.di.digital.dto.response.zonal.ZonalSubmissionResponse;
import org.di.digital.dto.response.zonal.ZonalSubmissionSummaryResponse;
import org.di.digital.service.zonal.ZonalTableService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/zonal")
@PreAuthorize("hasAuthority('ZONAL')")
public class ZonalTableController {

    private final ZonalTableService zonalTableService;

    @GetMapping("/forms")
    public ResponseEntity<List<ZonalFormSummaryResponse>> listForms() {
        return ResponseEntity.ok(zonalTableService.listForms());
    }

    @GetMapping("/forms/{templateId}")
    public ResponseEntity<ZonalFormResponse> getForm(@PathVariable Long templateId) {
        return ResponseEntity.ok(zonalTableService.getForm(templateId));
    }

    @PutMapping("/forms/{templateId}/rows")
    public ResponseEntity<ZonalFormResponse> saveRows(@PathVariable Long templateId,
                                                      @Valid @RequestBody ZonalRowsRequest request) {
        return ResponseEntity.ok(zonalTableService.saveRows(templateId, request));
    }

    @PostMapping("/forms/{templateId}/submit")
    public ResponseEntity<ZonalSubmissionResponse> submit(@PathVariable Long templateId,
                                                          @Valid @RequestBody ZonalRowsRequest request) {
        return ResponseEntity.ok(zonalTableService.submit(templateId, request));
    }

    @GetMapping("/submissions")
    public ResponseEntity<Page<ZonalSubmissionSummaryResponse>> getHistory(
            @RequestParam(required = false) Long templateId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(zonalTableService.getHistory(templateId, page, size));
    }

    @GetMapping("/submissions/{id}")
    public ResponseEntity<ZonalSubmissionResponse> getSubmission(@PathVariable Long id) {
        return ResponseEntity.ok(zonalTableService.getSubmission(id));
    }
}
