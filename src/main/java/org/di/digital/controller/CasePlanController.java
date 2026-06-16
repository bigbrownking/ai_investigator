package org.di.digital.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.plan.AddPlanActionRequest;
import org.di.digital.dto.request.plan.RejectPlanRequest;
import org.di.digital.dto.request.plan.ManualStatusRequest;
import org.di.digital.dto.response.plan.*;
import org.di.digital.model.plan.PlanNotification;
import org.di.digital.service.PlanApprovalService;
import org.di.digital.service.PlanService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/cases/plan")
@RequiredArgsConstructor
public class CasePlanController {

    private final PlanService planService;
    private final PlanApprovalService planApprovalService;

    @PostMapping("/generate")
    public ResponseEntity<CasePlanResponse> generatePlan(
            @RequestParam String caseNumber,
            @RequestParam String mode,
            Authentication authentication) {
        return ResponseEntity.ok(
                planService.generatePlan(caseNumber, mode, authentication.getName())
        );
    }

    @GetMapping
    public ResponseEntity<CasePlanResponse> getPlan(
            @RequestParam String caseNumber,
            Authentication authentication) {
        return ResponseEntity.ok(planService.getPlan(caseNumber, authentication.getName()));
    }

    @GetMapping("/management/pending")
    @PreAuthorize("hasAnyAuthority('REG_ADMIN', 'ADVANCED_USER')")
    public ResponseEntity<List<ManagementPendingPlanDto>> getManagementPendingPlans(
            Authentication authentication) {
        return ResponseEntity.ok(
                planService.getManagementPendingPlans(authentication.getName())
        );
    }

    @PatchMapping(value = "/action", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<CasePlanResponse> updatePlanField(
            @RequestParam String caseNumber,
            @RequestParam int actionNumber,
            @RequestParam String key,
            @RequestBody Object value,
            Authentication authentication) {
        return ResponseEntity.ok(
                planService.updatePlanField(
                        caseNumber, authentication.getName(), actionNumber, key, value)
        );
    }

    @PostMapping("/action/status")
    public ResponseEntity<ManualStatusResponse> updateActionStatus(
            @RequestParam String caseNumber,
            @RequestBody ManualStatusRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                planService.updateActionStatus(caseNumber, authentication.getName(), request)
        );
    }

    @PostMapping("/action")
    public ResponseEntity<CasePlanResponse> addAction(
            @RequestParam String caseNumber,
            @RequestBody AddPlanActionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                planService.addAction(caseNumber, authentication.getName(), request)
        );
    }

    @DeleteMapping("/action")
    public ResponseEntity<CasePlanResponse> deleteAction(
            @RequestParam String caseNumber,
            @RequestParam int actionNumber,
            Authentication authentication) {
        return ResponseEntity.ok(
                planService.deleteAction(caseNumber, authentication.getName(), actionNumber)
        );
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadPlan(
            @RequestParam String caseNumber,
            Authentication authentication) {
        Resource resource = planService.downloadPlanAsWord(caseNumber, authentication.getName());
        String filename = String.format("план_%s.docx", caseNumber.replace("/", "-"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(resource);
    }

    @PostMapping("/submit")
    public ResponseEntity<PlanSubmitResponse> submitPlan(
            @RequestParam String caseNumber,
            Authentication authentication) {
        return ResponseEntity.ok(
                planService.submitPlan(caseNumber, authentication.getName())
        );
    }

    @PostMapping("/approve")
    @PreAuthorize("hasAuthority('ADVANCED_USER')")
    public ResponseEntity<Void> approve(
            @RequestParam String caseNumber,
            Authentication authentication) {
        planApprovalService.approvePlan(authentication.getName(), caseNumber);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/final-approve")
    @PreAuthorize("hasAuthority('REG_ADMIN')")
    public ResponseEntity<Void> finalApprove(
            @RequestParam String caseNumber,
            Authentication authentication) {
        planApprovalService.finalApprovePlan(authentication.getName(), caseNumber);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reject")
    @PreAuthorize("hasAnyAuthority('REG_ADMIN', 'ADVANCED_USER')")
    public ResponseEntity<Void> reject(
            @RequestParam String caseNumber,
            @RequestBody @Valid RejectPlanRequest request,
            Authentication authentication) {
        planApprovalService.rejectPlan(authentication.getName(), caseNumber, request.getComment());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/withdraw")
    public ResponseEntity<Void> withdrawPlan(
            @RequestParam String caseNumber,
            Authentication authentication) {
        planApprovalService.withdrawPlan(authentication.getName(), caseNumber);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/approval-history")
    public ResponseEntity<List<PlanApprovalHistoryDto>> getHistory(
            @RequestParam String caseNumber,
            Authentication authentication) {
        return ResponseEntity.ok(
                planApprovalService.getHistory(authentication.getName(), caseNumber)
        );
    }

    @GetMapping("/edit-history")
    public ResponseEntity<List<PlanEditHistoryDto>> getEditHistory(
            @RequestParam String caseNumber,
            Authentication authentication) {
        return ResponseEntity.ok(
                planService.getEditHistory(caseNumber, authentication.getName())
        );
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<PlanNotification>> getMyNotifications(Authentication authentication) {
        return ResponseEntity.ok(planService.getMyNotifications(authentication.getName()));
    }

    @PatchMapping("/notifications/read")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        planService.markAllRead(authentication.getName());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markOneRead(
            @PathVariable Long id,
            Authentication authentication) {
        planService.markOneRead(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(Map.of("count", planService.getUnreadCount(authentication.getName())));
    }
}