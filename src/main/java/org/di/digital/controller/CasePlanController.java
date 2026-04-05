package org.di.digital.controller;

import jakarta.persistence.criteria.CriteriaBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.CaseFileResponse;
import org.di.digital.model.CaseFile;
import org.di.digital.service.PlanService;
import org.di.digital.util.Mapper;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/cases")
@RequiredArgsConstructor
public class CasePlanController {
    private final PlanService planService;

    @GetMapping("/{caseId}/plan")
    public ResponseEntity<CaseFileResponse> getPlan(@PathVariable Long caseId) {
        CaseFileResponse response = planService.getPlan(caseId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{caseId}/plan/exists")
    public ResponseEntity<Boolean> planExists(@PathVariable Long caseId) {
        return ResponseEntity.ok(planService.hasPlan(caseId));
    }

    @PostMapping("/{caseId}/plan/generate")
    public ResponseEntity<CaseFileResponse> generatePlan(
            @PathVariable Long caseId,
            Authentication authentication
    ) {
        CaseFileResponse response = planService.generatePlan(caseId, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/{caseId}/plan/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<CaseFileResponse>> addPlanFiles(
            @PathVariable Long caseId,
            @RequestPart("files") List<MultipartFile> files,
            Authentication authentication
    ) {
        return ResponseEntity.ok(planService.addPlanFiles(caseId, files, authentication.getName()));
    }

    @GetMapping("/{caseId}/plan/files")
    public ResponseEntity<List<CaseFileResponse>> getPlanFiles(@PathVariable Long caseId) {
        return ResponseEntity.ok(planService.getPlanFiles(caseId));
    }
}