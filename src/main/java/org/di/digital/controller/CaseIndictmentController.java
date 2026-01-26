package org.di.digital.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.service.IndictmentService;
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
@RequestMapping("/cases")
@RequiredArgsConstructor
public class CaseIndictmentController {
    private final IndictmentService indictmentService;

    @Operation(
            summary = "Stream indictment generation",
            description = "Generates indictment for a case and streams the response in real-time. " +
                    "Note: Swagger UI will show complete response after streaming finishes. " +
                    "For real-time testing, use: curl -N http://localhost:5556/cases/indictment/stream?caseNumber=YOUR_CASE_NUMBER"
    )
    @ApiResponse(
            responseCode = "200",
            description = "Streaming response",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)
    )
    @GetMapping(value = "/indictment/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamIndictment(@RequestParam(defaultValue = "245500121000001-216") String caseNumber,
                                          Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("Unauthenticated access attempt to qualification stream");
            SseEmitter emitter = new SseEmitter();
            emitter.completeWithError(new IllegalStateException("Authentication required"));
            return emitter;
        }

        log.info("Streaming qualification for workspace: {} by user: {}",
                caseNumber, authentication.getName());
        return indictmentService.generateIndictment(caseNumber);
    }



    @Operation(
            summary = "Download indictment as Word document",
            description = "Downloads the generated indictment for a case as a Word (.docx) document"
    )
    @ApiResponse(
            responseCode = "200",
            description = "Word document downloaded successfully",
            content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    )
    @GetMapping("/indictment/download")
    public ResponseEntity<Resource> downloadIndictment(
            @RequestParam String caseNumber,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("Unauthenticated access attempt to download qualification");
            return ResponseEntity.status(401).build();
        }

        log.info("Downloading qualification for case: {} by user: {}",
                caseNumber, authentication.getName());

        Resource resource = indictmentService.downloadIndictmentAsWord(caseNumber);

        String filename = String.format("Indictment_%s.docx", caseNumber.replace("/", "-"));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(resource);
    }

}
