package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.indictment.IndictmentSectionUpdateRequest;
import org.di.digital.dto.response.indictment.IndictmentSectionDto;
import org.di.digital.service.IndictmentService;
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
@RequestMapping("/cases/indictment")
@RequiredArgsConstructor
public class CaseIndictmentController {

    private final IndictmentService indictmentService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamIndictment(@RequestParam String caseNumber,
                                       @RequestParam(required = false, defaultValue = "russian") String language,
                                       Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("Unauthenticated access attempt to indictment stream");
            SseEmitter emitter = new SseEmitter();
            emitter.completeWithError(new IllegalStateException("Authentication required"));
            return emitter;
        }

        log.info("Generating indictment for case: {} by user: {}",
                caseNumber, authentication.getName());
        return indictmentService.generateIndictment(caseNumber, language, authentication.getName());
    }

    @GetMapping(value = "/stream/complete", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter complete(
            @RequestParam String caseNumber,
            @RequestParam(required = false, defaultValue = "russian") String language,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("Unauthenticated access attempt to indictment complete");
            SseEmitter emitter = new SseEmitter();
            emitter.completeWithError(new IllegalStateException("Authentication required"));
            return emitter;
        }

        log.info("Completing indictment for case: {} by user: {}",
                caseNumber, authentication.getName());
        return indictmentService.completeIndictment(caseNumber, language, authentication.getName());
    }

    @GetMapping(value = "/stream/section", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSection(@RequestParam String caseNumber,
                                    @RequestParam int sectionId,
                                    @RequestParam(required = false, defaultValue = "russian") String language,
                                    Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("Unauthenticated access attempt to indictment section");
            SseEmitter emitter = new SseEmitter();
            emitter.completeWithError(new IllegalStateException("Authentication required"));
            return emitter;
        }

        log.info("Generating indictment section {} for case: {} by user: {}",
                sectionId, caseNumber, authentication.getName());
        return indictmentService.generateIndictmentSection(
                caseNumber, language, authentication.getName(), sectionId);
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadIndictment(
            @RequestParam String caseNumber,
            Authentication authentication) {
        log.info("Downloading indictment for case: {} by user: {}",
                caseNumber, authentication.getName());

        Resource resource = indictmentService.downloadIndictmentAsWord(caseNumber, authentication.getName());

        String filename = String.format("обвинительный акт_%s.docx", caseNumber.replace("/", "-"));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encode(filename, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(resource);
    }

    @GetMapping("/sections")
    public ResponseEntity<List<IndictmentSectionDto>> getIndictmentSections(@RequestParam String caseNumber) {
        return ResponseEntity.ok(indictmentService.getIndictmentSections(caseNumber));
    }

    @PatchMapping("/sections")
    public ResponseEntity<IndictmentSectionDto> updateSection(
            @RequestParam String caseNumber,
            @RequestBody IndictmentSectionUpdateRequest request,
            Authentication authentication) {
        log.info("Updating indictment section {} for case: {} by user: {}",
                request.getId(), caseNumber, authentication.getName());
        return ResponseEntity.ok(indictmentService.updateSection(caseNumber, request));
    }
}