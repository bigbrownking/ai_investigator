package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.AppealDto;
import org.di.digital.dto.response.CaseResponse;
import org.di.digital.dto.response.UserProfile;
import org.di.digital.model.Appeal;
import org.di.digital.model.User;
import org.di.digital.security.UserDetailsImpl;
import org.di.digital.service.RegAdminService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/reg-admin/")
public class RegAdminController {

    private final RegAdminService regAdminService;
    @GetMapping("/appeals")
    public ResponseEntity<Page<AppealDto>> getAppeals(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.getMyRegionAppeals(userDetails.getId(), page, size));
    }

    @GetMapping("/users")
    public ResponseEntity<Page<UserProfile>> getUsers(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.getMyRegionUsers(userDetails.getId(), page, size));
    }
    @GetMapping("/cases")
    public ResponseEntity<Page<CaseResponse>> getRegionCases(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.getMyRegionCases(userDetails.getId(), page, size));
    }

    @GetMapping("/cases/{caseId}")
    public ResponseEntity<CaseResponse> getCaseDetail(
            @PathVariable Long caseId,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.getMyRegionCaseDetail(userDetails.getId(), caseId));
    }
    @GetMapping("/cases/{caseId}/indictment")
    public ResponseEntity<String> getCaseIndictment(
            @PathVariable Long caseId,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.getMyRegionCaseIndictment(userDetails.getId(), caseId));
    }
    @GetMapping("/cases/{caseId}/qualification")
    public ResponseEntity<String> getCaseQualification(
            @PathVariable Long caseId,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.getMyRegionCaseQualification(userDetails.getId(), caseId));
    }

    @GetMapping("/users/{userId}/cases")
    public ResponseEntity<Page<CaseResponse>> getUserCases(
            @PathVariable Long userId,
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.getUserCases(userDetails.getId(), userId, page, size));
    }
    @PutMapping("/appeals/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long id, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        regAdminService.approveAppeal(id, userDetails.getId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/appeals/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        regAdminService.rejectAppeal(id, userDetails.getId());
        return ResponseEntity.ok().build();
    }
}