package org.di.digital.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.AddUserToCaseRequest;
import org.di.digital.dto.request.CreateCaseRequest;
import org.di.digital.dto.request.FileType;
import org.di.digital.dto.response.CaseResponse;
import org.di.digital.dto.response.CaseUserResponse;
import org.di.digital.service.CaseService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/cases")
@RequiredArgsConstructor
public class CaseController {
    private final CaseService caseService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CaseResponse> createCase(
            @RequestParam("title") String title,
            @RequestParam("number") String number,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            Authentication authentication
    ) {
        log.info("Creating case with title: {} and number: {} for user: {}",
                title, number, authentication.getName());

        CreateCaseRequest request = CreateCaseRequest.builder()
                .title(title)
                .number(number)
                .description(description)
                .files(files)
                .build();

        CaseResponse response = caseService.createCase(request, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaseResponse> getCaseById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        log.info("Getting case with id: {} for user: {}", id, authentication.getName());
        CaseResponse response = caseService.getCaseById(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<CaseResponse>> getUserCases(
            @RequestParam(required = false, defaultValue = "desc") String sort,
            Authentication authentication
    ) {
        log.info("Getting all cases for user: {} with sort: {}", authentication.getName(), sort);
        List<CaseResponse> cases = caseService.getUserCases(authentication.getName(), sort);
        return ResponseEntity.ok(cases);
    }

    @PostMapping(value = "/{caseId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CaseResponse> addFilesToCase(
            @PathVariable Long caseId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("type") String type,
            Authentication authentication
    ) {
        FileType fileType;
        try {
            fileType = FileType.fromString(type);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid type parameter: {}", type);
            return ResponseEntity.badRequest().build();
        }

        log.info("Adding {} files to case: {} with type: {} for user: {}",
                files.size(), caseId, fileType, authentication.getName());

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        CaseResponse response = caseService.addFilesToCase(caseId, files, fileType, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{caseId}/status")
    public ResponseEntity<CaseResponse> updateCaseStatus(
            @PathVariable Long caseId,
            @RequestParam boolean status,
            Authentication authentication
    ) {
        log.info("Updating case {} status to {} by user: {}",
                caseId, status, authentication.getName());

        CaseResponse response = caseService.updateCaseStatus(caseId, status, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{caseId}/files")
    public ResponseEntity<CaseResponse> deleteFileFromCase(
            @PathVariable Long caseId,
            @RequestParam String fileName,
            Authentication authentication
    ) {
        log.info("Deleting file {} from case: {} for user: {}",
                fileName, caseId, authentication.getName());

        CaseResponse response = caseService.deleteFileFromCase(caseId, fileName, authentication.getName());
        return ResponseEntity.ok(response);
    }
    @GetMapping("/{caseId}/files/download")
    public ResponseEntity<InputStreamResource> downloadFile(
            @PathVariable Long caseId,
            @RequestParam String fileName,
            Authentication authentication
    ) {
        log.info("Downloading file from case: {} for user: {}", caseId, authentication.getName());

        InputStreamResource resource = caseService.downloadFile(caseId, fileName, authentication.getName());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileName, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PostMapping("/{caseId}/users")
    public ResponseEntity<CaseResponse> addUserToCase(
            @PathVariable Long caseId,
            @Valid @RequestBody AddUserToCaseRequest request,
            Authentication authentication
    ) {
        log.info("Adding user {} to case: {} by user: {}",
                request.getEmail(), caseId, authentication.getName());

        CaseResponse response = caseService.addUserToCase(caseId, request.getEmail(), authentication.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{caseId}/users/{userId}")
    public ResponseEntity<CaseResponse> removeUserFromCase(
            @PathVariable Long caseId,
            @PathVariable Long userId,
            Authentication authentication
    ) {
        log.info("Removing user {} from case: {} by user: {}",
                userId, caseId, authentication.getName());

        CaseResponse response = caseService.removeUserFromCase(caseId, userId, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{caseId}/users")
    public ResponseEntity<List<CaseUserResponse>> getCaseUsers(
            @PathVariable Long caseId,
            Authentication authentication
    ) {
        log.info("Getting users for case: {} by user: {}", caseId, authentication.getName());

        List<CaseUserResponse> users = caseService.getCaseUsers(caseId, authentication.getName());
        return ResponseEntity.ok(users);
    }

    @GetMapping("/recent")
    public ResponseEntity<Page<CaseResponse>> getRecentCases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        log.info("Getting recent cases for user: {}", authentication.getName());

        Page<CaseResponse> recentCases = caseService.getRecentCases(
                authentication.getName(),
                page,
                size
        );

        return ResponseEntity.ok(recentCases);
    }

    @GetMapping("/activity/{activityType}")
    public ResponseEntity<Page<CaseResponse>> getCasesByActivity(
            @PathVariable String activityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        log.info("Getting cases with activity type {} for user: {}", activityType, authentication.getName());

        Page<CaseResponse> cases = caseService.getCasesByActivityType(
                authentication.getName(),
                activityType,
                page,
                size
        );

        return ResponseEntity.ok(cases);
    }
}
