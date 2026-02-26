package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.AddInterrogationRequest;
import org.di.digital.dto.request.TimerActionRequest;
import org.di.digital.dto.request.UpdateProtocolFieldRequest;
import org.di.digital.dto.response.*;
import org.di.digital.service.CaseInterrogationService;
import org.di.digital.service.InterrogationExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

import static java.net.URLEncoder.encode;

@Slf4j
@RestController
@RequestMapping("/cases")
@RequiredArgsConstructor
public class CaseInterrogationController {

    private final CaseInterrogationService caseInterrogationService;
    private final InterrogationExportService interrogationExportService;

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

        List<CaseInterrogationResponse> interrogations = caseInterrogationService.searchInterrogations(
                caseId, role, fio, date, authentication.getName()
        );

        return ResponseEntity.ok(interrogations);
    }

    @GetMapping("/{caseId}/{interrogationId}")
    public ResponseEntity<CaseInterrogationFullResponse> getDetailedInterrogation(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                caseInterrogationService.getDetailed(caseId, interrogationId, authentication.getName())
        );
    }

    @PatchMapping("/{caseId}/{interrogationId}/complete")
    public ResponseEntity<Void> completeInterrogation(
            @PathVariable long caseId,
            @PathVariable long interrogationId,
            Authentication authentication
    ){
        caseInterrogationService.completeInterrogation(caseId, interrogationId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{caseId}/interrogations")
    public ResponseEntity<CaseResponse> addInterrogation(
            @PathVariable Long caseId,
            @RequestBody AddInterrogationRequest request,
            Authentication authentication
    ) {
        log.info("Adding interrogation to case: {} for user: {}", caseId, authentication.getName());
        CaseResponse response = caseInterrogationService.addInterrogation(caseId, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{caseId}/interrogations/{interrogationId}/protocol")
    public ResponseEntity<Void> updateProtocolField(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            @RequestBody UpdateProtocolFieldRequest request,
            Authentication authentication
    ) {
        caseInterrogationService.updateProtocolField(
                caseId, interrogationId, request, authentication.getName()
        );
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{caseId}/interrogations/{interrogationId}")
    public ResponseEntity<CaseResponse> deleteInterrogation(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            Authentication authentication
    ) {
        log.info("Deleting interrogation from case: {} for user: {}", caseId, authentication.getName());
        CaseResponse response = caseInterrogationService.deleteInterrogation(caseId, interrogationId, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/{caseId}/interrogations/{interrogationId}/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<QAResponse> uploadAudio(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            @RequestParam String question,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "ru") String language,
            Authentication authentication
    ) {
        log.info("Uploading audio for interrogation: {}, case: {}", interrogationId, caseId);
        QAResponse response = caseInterrogationService.uploadAudioAndEnqueue(
                caseId, interrogationId, question, file, language, authentication.getName()
        );
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{caseId}/interrogations/{interrogationId}/qa")
    public ResponseEntity<List<QAResponse>> getQAList(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            Authentication authentication
    ) {
        List<QAResponse> qaList = caseInterrogationService.getQAList(caseId, interrogationId, authentication.getName());
        return ResponseEntity.ok(qaList);
    }

    @PatchMapping("/{caseId}/interrogations/{interrogationId}/timer")
    public ResponseEntity<Void> controlTimer(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            @RequestBody TimerActionRequest request,
            Authentication authentication
    ) {

        caseInterrogationService.controlTimer(caseId, interrogationId, request.getAction(), authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{caseId}/interrogations/{interrogationId}/download")
    public ResponseEntity<byte[]> downloadInterrogation(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            Authentication authentication
    ) {
        CaseInterrogationFullResponse data = caseInterrogationService
                .getDetailed(caseId, interrogationId, authentication.getName());

        byte[] docx = interrogationExportService.exportToDocx(data);

        String filename = "допрос_" + interrogationId + "_" + data.getFio().replace(" ", "_") + ".docx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encode(filename, java.nio.charset.StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docx);
    }
}
