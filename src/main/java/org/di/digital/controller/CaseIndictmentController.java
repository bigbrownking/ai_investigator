package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.indictment.IndictmentRephraseApplyRequest;
import org.di.digital.dto.request.indictment.IndictmentRephraseRequest;
import org.di.digital.dto.request.indictment.IndictmentSectionUpdateRequest;
import org.di.digital.dto.response.indictment.IndictmentSectionDto;
import org.di.digital.service.indictment.IndictmentService;
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
                                       Authentication authentication) {
        log.info("Generating indictment for case: {} by user: {}",
                caseNumber, authentication.getName());
        return indictmentService.generateIndictment(caseNumber, authentication.getName());
    }

    @GetMapping(value = "/stream/complete", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter complete(
            @RequestParam String caseNumber,
            Authentication authentication) {
        log.info("Completing indictment for case: {} by user: {}",
                caseNumber, authentication.getName());
        return indictmentService.completeIndictment(caseNumber, authentication.getName());
    }

    @GetMapping(value = "/stream/section", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSection(@RequestParam String caseNumber,
                                    @RequestParam int sectionId,
                                    Authentication authentication) {
        log.info("Generating indictment section {} for case: {} by user: {}",
                sectionId, caseNumber, authentication.getName());
        return indictmentService.generateIndictmentSection(
                caseNumber, authentication.getName(), sectionId);
    }

    @PostMapping(value = "/stream/rephrase", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRephrase(@RequestParam String caseNumber,
                                     @RequestBody IndictmentRephraseRequest request,
                                     Authentication authentication) {
        return indictmentService.generateIndictmentPrompt(
                caseNumber, authentication.getName(),
                request.getStartSectionId(), request.getStartOffset(),
                request.getEndSectionId(), request.getEndOffset(), request.getPrompt());
    }

    @PostMapping("/rephrase/apply")
    public ResponseEntity<List<IndictmentSectionDto>> applyRephrase(
            @RequestParam String caseNumber,
            @RequestBody IndictmentRephraseApplyRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        log.info("Applying rephrase for case: {} by user: {}", caseNumber, email);
        return ResponseEntity.ok(indictmentService.applyRephrase(caseNumber, email,request));
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
    public ResponseEntity<List<IndictmentSectionDto>> getIndictmentSections(@RequestParam String caseNumber,
                                                                            Authentication authentication) {
        return ResponseEntity.ok(indictmentService.getIndictmentSections(caseNumber, authentication.getName()));
    }

    @PatchMapping("/sections")
    public ResponseEntity<IndictmentSectionDto> updateSection(
            @RequestParam String caseNumber,
            @RequestBody IndictmentSectionUpdateRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        log.info("Updating indictment section {} for case: {} by user: {}",
                request.getId(), caseNumber, email);
        return ResponseEntity.ok(indictmentService.updateSection(caseNumber, email, request));
    }
}