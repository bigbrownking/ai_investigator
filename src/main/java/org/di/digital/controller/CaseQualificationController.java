package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.qualification.QualificationRephraseApplyRequest;
import org.di.digital.dto.request.qualification.QualificationRephraseRequest;
import org.di.digital.dto.request.qualification.QualificationSectionUpdateRequest;
import org.di.digital.dto.response.qualification.QualificationSectionDto;
import org.di.digital.service.qualification.QualificationService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static java.net.URLEncoder.encode;

@Slf4j
@RestController
@RequestMapping("/cases/qualification")
@RequiredArgsConstructor
public class CaseQualificationController {

    private final QualificationService qualificationService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamQualification(@RequestParam String caseNumber,
                                          Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("Unauthenticated access attempt to qualification stream");
            SseEmitter emitter = new SseEmitter();
            emitter.completeWithError(new IllegalStateException("Authentication required"));
            return emitter;
        }
        log.info("Generating qualification for case: {} by user: {}",
                caseNumber, authentication.getName());
        return qualificationService.generateQualification(caseNumber, authentication.getName());
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadQualification(
            @RequestParam String caseNumber,
            Authentication authentication) {
        log.info("Downloading qualification for case: {} by user: {}",
                caseNumber, authentication.getName());

        Resource resource = qualificationService.downloadQualificationAsWord(caseNumber, authentication.getName());

        String filename = String.format("квалификация_%s.docx", caseNumber.replace("/", "-"));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encode(filename, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(resource);
    }

    @GetMapping("/sections")
    public ResponseEntity<List<QualificationSectionDto>> getQualificationSections(
            @RequestParam String caseNumber) {
        return ResponseEntity.ok(qualificationService.getQualificationSections(caseNumber));
    }

    @GetMapping(value = "/stream/section", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSection(@RequestParam String caseNumber,
                                    @RequestParam int sectionId,
                                    Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            SseEmitter emitter = new SseEmitter();
            emitter.completeWithError(new IllegalStateException("Authentication required"));
            return emitter;
        }
        return qualificationService.generateQualificationSection(
                caseNumber, authentication.getName(), sectionId);
    }

    @PostMapping(value = "/stream/rephrase", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRephrase(@RequestParam String caseNumber,
                                     @RequestBody QualificationRephraseRequest request,
                                     Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            SseEmitter emitter = new SseEmitter();
            emitter.completeWithError(new IllegalStateException("Authentication required"));
            return emitter;
        }
        return qualificationService.generateQualificationPrompt(
                caseNumber, authentication.getName(),
                request.getStartSectionId(), request.getStartOffset(),
                request.getEndSectionId(), request.getEndOffset(), request.getPrompt());
    }

    @PostMapping("/rephrase/apply")
    public ResponseEntity<List<QualificationSectionDto>> applyRephrase(
            @RequestParam String caseNumber,
            @RequestBody QualificationRephraseApplyRequest request,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        log.info("Applying qualification rephrase for case: {} by user: {}",
                caseNumber, authentication.getName());
        return ResponseEntity.ok(qualificationService.applyRephrase(caseNumber, request));
    }

    @PatchMapping("/sections")
    public ResponseEntity<QualificationSectionDto> updateSection(
            @RequestParam String caseNumber,
            @RequestBody QualificationSectionUpdateRequest request,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        log.info("Updating qualification section {} for case: {} by user: {}",
                request.getId(), caseNumber, authentication.getName());
        return ResponseEntity.ok(qualificationService.updateSection(caseNumber, request));
    }
}