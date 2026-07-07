package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.cases.ChangeOwnerRequest;
import org.di.digital.dto.request.user.UpdateProfileRequest;
import org.di.digital.dto.request.auth.SignUpRequest;
import org.di.digital.dto.request.search.AppealSearchRequest;
import org.di.digital.dto.request.search.CaseSearchRequest;
import org.di.digital.dto.request.search.UserSearchRequest;
import org.di.digital.dto.response.*;
import org.di.digital.dto.response.admin.AdminStatsDto;
import org.di.digital.dto.response.admin.AppealDto;
import org.di.digital.dto.response.admin.RegionStatsDto;
import org.di.digital.dto.response.admin.RegionSummaryDto;
import org.di.digital.dto.response.cases.CasePageResponse;
import org.di.digital.dto.response.cases.CaseResponse;
import org.di.digital.dto.response.interrogation.CaseInterrogationFullResponse;
import org.di.digital.dto.response.plan.CasePlanResponse;
import org.di.digital.dto.response.support.ReviewDto;
import org.di.digital.dto.response.support.SupportTicketDto;
import org.di.digital.dto.response.user.UserProfile;
import org.di.digital.security.UserDetailsImpl;
import org.di.digital.service.AdminService;
import org.di.digital.service.auth.AuthService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static java.net.URLEncoder.encode;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
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
            @ModelAttribute UserSearchRequest userSearchRequest) {
        return ResponseEntity.ok(adminService.getAllUsers(page, size, userSearchRequest));
    }

    @GetMapping("/users/{userId}/cases")
    public ResponseEntity<CasePageResponse> getUserCases(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @ModelAttribute CaseSearchRequest caseSearchRequest) {
        return ResponseEntity.ok(adminService.getUserCases(userId, page, size, caseSearchRequest));
    }

    @GetMapping("/cases")
    public ResponseEntity<CasePageResponse> getAllCases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @ModelAttribute CaseSearchRequest caseSearchRequest) {
        return ResponseEntity.ok(adminService.getAllCases(page, size, caseSearchRequest));
    }

    @GetMapping("/cases/{caseId}")
    public ResponseEntity<CaseResponse> getCaseDetail(@PathVariable Long caseId) {
        return ResponseEntity.ok(adminService.getCaseDetail(caseId));
    }
    @GetMapping("/interrogations/{id}")
    public ResponseEntity<CaseInterrogationFullResponse> getInterrogationDetail(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getInterrogationDetail(id));
    }

    @GetMapping("/interrogations/{id}/download")
    public ResponseEntity<byte[]> downloadInterrogation(@PathVariable Long id) {
        CaseInterrogationFullResponse data = adminService.getInterrogationDetail(id);
        byte[] docx = adminService.downloadInterrogation(id);

        String filename = "допрос_" + id + "_" + data.getFio().replace(" ", "_") + ".docx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encode(filename, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docx);
    }

    @GetMapping("/appeals")
    public ResponseEntity<Page<AppealDto>> getAllAppeals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @ModelAttribute AppealSearchRequest appealSearchRequest) {
        return ResponseEntity.ok(adminService.getAllAppeals(page, size, appealSearchRequest));
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
    @PutMapping("/appeals/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long id, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        adminService.approveAppeal(id, userDetails.getId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/appeals/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        adminService.rejectAppeal(id, userDetails.getId());
        return ResponseEntity.ok().build();
    }
    @GetMapping("/logs/user/{email}")
    public ResponseEntity<Page<LogDto>> getUserLogs(
            @PathVariable String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.getUserLogs(email, page, size));
    }

    @GetMapping("/support")
    public ResponseEntity<Page<SupportTicketDto>> getAllSupportTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.getAllSupportTickets(page, size));
    }
    @GetMapping("/support/{id}")
    public ResponseEntity<SupportTicketDto> getSupportTicketDetail(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getSupportTicketDetail(id));
    }

    @GetMapping("/reviews")
    public ResponseEntity<Page<ReviewDto>> getAllReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.getAllReviews(page, size));
    }

    @GetMapping("/reviews/{id}")
    public ResponseEntity<ReviewDto> getReviewDetail(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getReviewDetail(id));
    }

    @PutMapping("/users/assign-advanced")
    public ResponseEntity<Void> assignAdvanced(@RequestParam String email) {
        adminService.assignAdvancedUserRole(email);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/assign-reg-admin")
    public ResponseEntity<Void> assignRegAdmin(
            @RequestParam String email,
            @RequestParam List<String> regions) {
        adminService.assignRegAdminRole(email, regions);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/remove-reg-admin")
    public ResponseEntity<Void> removeRegAdmin(
            @RequestParam String email,
            @RequestParam List<String> regions) {
        adminService.removeRegAdminRole(email, regions);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/cases/{caseId}/owner")
    public ResponseEntity<Void> changeOwner(
            @PathVariable Long caseId,
            @RequestBody ChangeOwnerRequest request) {
        adminService.changeOwner(caseId, request.getNewOwnerEmail());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/{userId}/profile")
    public ResponseEntity<UserProfile> updateUserProfile(
            @PathVariable Long userId,
            @RequestBody UpdateProfileRequest request){
        return ResponseEntity.ok(adminService.updateUserProfile(userId, request));
    }


    @GetMapping("/cases/{caseId}/indictment")
    public ResponseEntity<String> getIndictment(@PathVariable Long caseId) {
        return ResponseEntity.ok(adminService.getIndictment(caseId));
    }

    @GetMapping("/cases/{caseId}/qualification")
    public ResponseEntity<String> getQualification(@PathVariable Long caseId) {
        return ResponseEntity.ok(adminService.getQualification(caseId));
    }

    @GetMapping("/cases/{caseId}/plan")
    public ResponseEntity<CasePlanResponse> getPlan(@PathVariable Long caseId) {
        return ResponseEntity.ok(adminService.getPlan(caseId));
    }
}
