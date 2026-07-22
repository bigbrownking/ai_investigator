package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
}