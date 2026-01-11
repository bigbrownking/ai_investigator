package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.AddInterrogationRequest;
import org.di.digital.dto.request.CreateCaseRequest;
import org.di.digital.dto.response.CaseInterrogationResponse;
import org.di.digital.dto.response.CaseResponse;
import org.di.digital.service.CaseService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
    public ResponseEntity<List<CaseResponse>> getUserCases(Authentication authentication) {
        log.info("Getting all cases for user: {}", authentication.getName());
        List<CaseResponse> cases = caseService.getUserCases(authentication.getName());
        return ResponseEntity.ok(cases);
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

    @GetMapping("/{caseId}/interrogations")
    public ResponseEntity<List<CaseInterrogationResponse>> getInterrogations(
            @PathVariable Long caseId,
            @RequestParam(required = false, defaultValue = "Все") String role,
            @RequestParam(required = false) String fio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication
    ) {
        log.info("Searching interrogations in case: {} with filters - role: {}, fio: {}, date: {}",
                caseId, role, fio, date);

        List<CaseInterrogationResponse> interrogations = caseService.searchInterrogations(
                caseId, role, fio, date, authentication.getName()
        );

        return ResponseEntity.ok(interrogations);
    }

    @PostMapping("/{caseId}/interrogations")
    public ResponseEntity<CaseResponse> addInterrogation(
            @PathVariable Long caseId,
            @RequestBody AddInterrogationRequest request,
            Authentication authentication
    ) {
        log.info("Adding interrogation to case: {} for user: {}", caseId, authentication.getName());
        CaseResponse response = caseService.addInterrogation(caseId, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

}
