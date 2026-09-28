package org.di.digital.reporting.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.di.digital.reporting.dto.request.CreateTemplateRequest;
import org.di.digital.reporting.dto.request.UpdateTemplateRequest;
import org.di.digital.reporting.dto.response.TemplateResponse;
import org.di.digital.reporting.dto.response.TemplateSummaryResponse;
import org.di.digital.reporting.service.TemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Template management for main admins (ADMIN authority).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reporting/admin/templates")
@PreAuthorize("hasAuthority('ADMIN')")
public class TemplateAdminController {

    private final TemplateService templateService;

    @GetMapping
    public ResponseEntity<List<TemplateSummaryResponse>> listForms() {
        return ResponseEntity.ok(templateService.listForms());
    }

    @PostMapping
    public ResponseEntity<TemplateResponse> createForm(@Valid @RequestBody CreateTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.createForm(request));
    }

    @GetMapping("/{code}/versions")
    public ResponseEntity<List<TemplateResponse>> getVersions(@PathVariable String code) {
        return ResponseEntity.ok(templateService.getVersions(code));
    }

    @GetMapping("/{code}/versions/{version}")
    public ResponseEntity<TemplateResponse> getVersion(@PathVariable String code, @PathVariable Integer version) {
        return ResponseEntity.ok(templateService.getVersion(code, version));
    }

    @GetMapping("/{code}/active")
    public ResponseEntity<TemplateResponse> getActive(@PathVariable String code) {
        return ResponseEntity.ok(templateService.getActive(code));
    }

    @PostMapping("/{code}/drafts")
    public ResponseEntity<TemplateResponse> createDraft(@PathVariable String code) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.createDraft(code));
    }

    @PutMapping("/{code}/versions/{version}")
    public ResponseEntity<TemplateResponse> updateDraft(
            @PathVariable String code,
            @PathVariable Integer version,
            @Valid @RequestBody UpdateTemplateRequest request) {
        return ResponseEntity.ok(templateService.updateDraft(code, version, request));
    }

    @DeleteMapping("/{code}/versions/{version}")
    public ResponseEntity<Void> deleteDraft(@PathVariable String code, @PathVariable Integer version) {
        templateService.deleteDraft(code, version);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{code}/versions/{version}/publish")
    public ResponseEntity<TemplateResponse> publish(@PathVariable String code, @PathVariable Integer version) {
        return ResponseEntity.ok(templateService.publish(code, version));
    }
}
