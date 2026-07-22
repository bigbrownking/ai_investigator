package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.cases.ChangeOwnerRequest;
import org.di.digital.dto.request.search.AppealSearchRequest;
import org.di.digital.dto.request.search.CaseSearchRequest;
import org.di.digital.dto.request.search.UserSearchRequest;
import org.di.digital.dto.response.*;
import org.di.digital.dto.response.admin.AppealDto;
import org.di.digital.dto.response.admin.RegionStatsDto;
import org.di.digital.dto.response.cases.CasePageResponse;
import org.di.digital.dto.response.cases.CaseResponse;
import org.di.digital.dto.response.interrogation.CaseInterrogationFullResponse;
import org.di.digital.dto.response.user.UserProfile;
import org.di.digital.dto.response.user.UserSuggestionResponse;
import org.di.digital.security.UserDetailsImpl;
import org.di.digital.service.admin.RegAdminService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static java.net.URLEncoder.encode;
import static org.di.digital.util.requests.UserUtil.getCurrentUser;

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
            @RequestParam(defaultValue = "20") int size,
            @ModelAttribute AppealSearchRequest appealSearchRequest) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.getMyRegionAppeals(userDetails.getId(), page, size, appealSearchRequest));
    }

    @GetMapping("/users")
    public ResponseEntity<Page<UserProfile>> getUsers(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @ModelAttribute UserSearchRequest userSearchRequest) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.getMyRegionUsers(userDetails.getId(), page, size, userSearchRequest));
    }
    @GetMapping("/cases")
    public ResponseEntity<CasePageResponse> getRegionCases(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @ModelAttribute CaseSearchRequest caseSearchRequest) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.getMyRegionCases(userDetails.getId(), page, size, caseSearchRequest));
    }

    @GetMapping("/cases/{caseId}")
    public ResponseEntity<CaseResponse> getCaseDetail(
            @PathVariable Long caseId,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.getMyRegionCaseDetail(userDetails.getId(), caseId));
    }

    @GetMapping("/users/{userId}/cases")
    public ResponseEntity<CasePageResponse> getUserCases(
            @PathVariable Long userId,
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @ModelAttribute CaseSearchRequest caseSearchRequest) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.getUserCases(userDetails.getId(), userId, page, size, caseSearchRequest));
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
    @GetMapping("/logs/user/{email}")
    public ResponseEntity<Page<LogDto>> getMyRegionUserLogs(
            @PathVariable String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long adminId = getCurrentUser().getId();
        return ResponseEntity.ok(regAdminService.getMyRegionUserLogs(adminId, email, page, size));
    }

    @GetMapping("/interrogations/{id}")
    public ResponseEntity<CaseInterrogationFullResponse> getInterrogationDetail(
            @PathVariable Long id, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.getMyRegionInterrogationDetail(userDetails.getId(), id));
    }

    @GetMapping("/interrogations/{id}/download")
    public ResponseEntity<byte[]> downloadInterrogation(
            @PathVariable Long id, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        CaseInterrogationFullResponse data = regAdminService.getMyRegionInterrogationDetail(userDetails.getId(), id);
        byte[] docx = regAdminService.downloadMyRegionInterrogation(userDetails.getId(), id);

        String filename = "допрос_" + id + "_" + data.getFio().replace(" ", "_") + ".docx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encode(filename, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docx);
    }

    @GetMapping("/stats")
    public ResponseEntity<RegionStatsDto> getStats(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.getMyRegionStats(userDetails.getId()));
    }

    @PatchMapping("/cases/{caseId}/owner")
    public ResponseEntity<Void> changeOwner(
            @PathVariable Long caseId,
            @RequestBody ChangeOwnerRequest request,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        regAdminService.changeOwner(userDetails.getId(), caseId, request.getUserId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/search")
    public ResponseEntity<List<UserSuggestionResponse>> searchUsers(
            @RequestParam String query,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.searchUsers(userDetails.getId(), query));
    }

    @GetMapping("/cases/{caseId}/indictment")
    public ResponseEntity<String> getIndictment(@PathVariable Long caseId,Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.getMyRegionIndictment(userDetails.getId(), caseId));
    }

    @GetMapping("/cases/{caseId}/qualification")
    public ResponseEntity<String> getQualification(@PathVariable Long caseId,Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.getMyRegionQualification(userDetails.getId(), caseId));
    }

    @GetMapping("/cases/{caseId}/plan")
    public ResponseEntity<Map<String, Object>> getPlan(@PathVariable Long caseId,Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(regAdminService.getMyRegionPlan(userDetails.getId(), caseId));
    }
}