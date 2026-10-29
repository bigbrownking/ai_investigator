package org.di.digital.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.di.digital.dto.request.zonal.ZonalTemplateRequest;
import org.di.digital.dto.response.zonal.ZonalTemplateResponse;
import org.di.digital.dto.response.zonal.ZonalTemplateSummaryResponse;
import org.di.digital.security.zonal.ReportingAccess;
import org.di.digital.service.zonal.ZonalTemplateAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/zonal/templates")
@PreAuthorize(ReportingAccess.ADMIN_OR_AFM_REG_ADMIN)
public class ZonalTemplateAdminController {

    private final ZonalTemplateAdminService templateService;

    @GetMapping
    public ResponseEntity<List<ZonalTemplateSummaryResponse>> listTemplates() {
        return ResponseEntity.ok(templateService.listTemplates());
    }

    @PostMapping
    public ResponseEntity<ZonalTemplateResponse> createTemplate(@Valid @RequestBody ZonalTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.createTemplate(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ZonalTemplateResponse> getTemplate(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getTemplate(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ZonalTemplateResponse> updateTemplate(@PathVariable Long id,
                                                                @Valid @RequestBody ZonalTemplateRequest request) {
        return ResponseEntity.ok(templateService.updateTemplate(id, request));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<ZonalTemplateResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.setActive(id, true));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ZonalTemplateResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.setActive(id, false));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }
}
