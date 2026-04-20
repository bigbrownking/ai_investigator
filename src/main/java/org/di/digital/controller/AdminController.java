package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.SignUpRequest;
import org.di.digital.dto.response.*;
import org.di.digital.service.AdminService;
import org.di.digital.service.AuthService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/")
public class AdminController {

    private final AdminService adminService;
    private final AuthService authService;

    @PostMapping("/reg_admin")
    public ResponseEntity<String> regAdmin(@RequestBody SignUpRequest signUpRequest) {
        return ResponseEntity.ok(authService.signupRegAdmin(signUpRequest));
    }
    @GetMapping("/users")
    public ResponseEntity<Page<UserProfile>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long regionId) {
        return ResponseEntity.ok(adminService.getAllUsers(page, size, regionId));
    }
    @GetMapping("/users/{userId}/cases")
    public ResponseEntity<Page<CaseResponse>> getUserCases(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.getUserCases(userId, page, size));
    }

    @GetMapping("/cases")
    public ResponseEntity<Page<CaseResponse>> getAllCases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long regionId) {
        return ResponseEntity.ok(adminService.getAllCases(page, size, regionId));
    }
    @GetMapping("/cases/{caseId}")
    public ResponseEntity<CaseResponse> getCaseDetail(@PathVariable Long caseId) {
        return ResponseEntity.ok(adminService.getCaseDetail(caseId));
    }

    @GetMapping("/cases/{caseId}/indictment")
    public ResponseEntity<String> getCaseIndictment(@PathVariable Long caseId) {
        return ResponseEntity.ok(adminService.getCaseIndictment(caseId));
    }

    @GetMapping("/cases/{caseId}/qualification")
    public ResponseEntity<String> getCaseQualification(@PathVariable Long caseId) {
        return ResponseEntity.ok(adminService.getCaseQualification(caseId));
    }

    @GetMapping("/appeals")
    public ResponseEntity<Page<AppealDto>> getAllAppeals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long regionId) {
        return ResponseEntity.ok(adminService.getAllAppeals(page, size, regionId));
    }

    @GetMapping("/map/stats")
    public ResponseEntity<List<RegionStatsDto>> getMapStats() {
        return ResponseEntity.ok(adminService.getRegionMapStats());
    }
    @GetMapping("/stats")
    public ResponseEntity<AdminStatsDto> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @PutMapping("/users/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        adminService.activateUser(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        adminService.deactivateUser(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/region/{regionId}/summary")
    public ResponseEntity<RegionSummaryDto> getRegionSummary(
            @PathVariable Long regionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.getRegionSummary(regionId, page, size));
    }
}
