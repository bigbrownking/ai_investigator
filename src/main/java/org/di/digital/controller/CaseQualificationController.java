package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.service.QualificationService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

        log.info("Streaming qualification for workspace: {} by user: {}",
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
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(resource);
    }

    @GetMapping
    public ResponseEntity<String> getQualification(
            @RequestParam String caseNumber){
        return ResponseEntity.ok(qualificationService.getQualification(caseNumber));
    }

}
