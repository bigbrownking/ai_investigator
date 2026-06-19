package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.interrogation.*;
import org.di.digital.dto.response.interrogation.*;
import org.di.digital.service.interrogation.CaseInterrogationService;
import org.di.digital.service.export.interrogation.InterrogationExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static java.net.URLEncoder.encode;
import static org.di.digital.util.requests.UserUtil.getCurrentUser;

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
            @RequestParam(required = false) Boolean isDop,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication
    ) {
        log.info("Searching interrogations in case: {} with filters - role: {}, fio: {}, date: {}",
                caseId, role, fio, date);

        List<CaseInterrogationResponse> interrogations = caseInterrogationService.searchInterrogations(
                caseId, role, fio, isDop, date, authentication.getName()
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
    public ResponseEntity<CaseInterrogationFullResponse> addInterrogation(
            @PathVariable Long caseId,
            @RequestBody AddInterrogationRequest request,
            Authentication authentication
    ) {
        log.info("Adding interrogation to case: {} for user: {}", caseId, authentication.getName());
        CaseInterrogationFullResponse response = caseInterrogationService.addInterrogation(caseId, request, authentication.getName());
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

    @PatchMapping("/{caseId}/interrogations/{interrogationId}/other")
    public ResponseEntity<Void> updateOtherField(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            @RequestBody UpdateProtocolFieldRequest request,
            Authentication authentication
    ) {
        caseInterrogationService.updateOtherField(
                caseId, interrogationId, request, authentication.getName()
        );
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{caseId}/interrogations/{interrogationId}")
    public ResponseEntity<Void> deleteInterrogation(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            Authentication authentication
    ) {
        log.info("Deleting interrogation from case: {} for user: {}", caseId, authentication.getName());
        caseInterrogationService.deleteInterrogation(caseId, interrogationId, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/{caseId}/interrogations/{interrogationId}/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<QAResponse> uploadAudio(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            @RequestParam(required = false) Long qaId,
            @RequestParam String question,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        log.info("Uploading audio for interrogation: {}, case: {}", interrogationId, caseId);
        QAResponse response = caseInterrogationService.uploadAudioAndEnqueue(
                caseId, interrogationId, qaId, question, file, authentication.getName()
        );
        return ResponseEntity.accepted().body(response);
    }

    @PostMapping(value = "/{caseId}/interrogations/{interrogationId}/otherAudio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OtherAudioResponse> uploadOtherAudio(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            @RequestParam(required = false) Long otherAudioId,
            @RequestParam String fieldName,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "ru") String language,
            Authentication authentication
    ) {
        log.info("Uploading audio for interrogation: {}, case: {}", interrogationId, caseId);
        OtherAudioResponse response = caseInterrogationService.uploadOtherAudioAndEnqueue(
                caseId, interrogationId, otherAudioId, fieldName, file, language, authentication.getName()
        );
        return ResponseEntity.accepted().body(response);
    }

    @PatchMapping("/{caseId}/interrogations/{interrogationId}/audio")
    public ResponseEntity<QAResponse> editTranscribedText(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            @RequestBody EditAudioTranscribedTextRequest request,
            Authentication authentication
    ){
        log.info("Editing audio for interrogation: {}, case: {}", interrogationId, caseId);
        QAResponse response = caseInterrogationService.editTranscribedText(
                caseId, interrogationId, request, authentication.getName()
        );
        return ResponseEntity.accepted().body(response);
    }

    @PatchMapping("/{caseId}/interrogations/{interrogationId}/otherAudio")
    public ResponseEntity<OtherAudioResponse> editOtherAudioText(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            @RequestBody EditOtherAudioTextRequest request,
            Authentication authentication) {
        return ResponseEntity.accepted().body(
                caseInterrogationService.editOtherAudioText(
                        caseId, interrogationId, request.getOtherAudioId(),
                        request.getText(), authentication.getName())
        );
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
            @RequestParam String action,
            Authentication authentication
    ) {

        caseInterrogationService.controlTimer(caseId, interrogationId, action, authentication.getName());
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

        byte[] docx = interrogationExportService.exportToDocx(data, getCurrentUser());

        String filename = "допрос_" + interrogationId + "_" + data.getFio().replace(" ", "_") + ".docx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encode(filename, java.nio.charset.StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docx);
    }

    @PostMapping(value = "/{caseId}/interrogations/{interrogationId}/application-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<CaseInterrogationApplicationFileResponse>> uploadApplicationFiles(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestPart(value = "meta", required = false) ApplicationFileUploadRequest meta,
            Authentication authentication
    ) {
        Map<String, String> displayNames = (meta != null && meta.getDisplayNames() != null)
                ? meta.getDisplayNames()
                : Map.of();

        return ResponseEntity.ok(caseInterrogationService
                .uploadApplicationFiles(caseId, interrogationId, files, displayNames, authentication.getName()));
    }

    @DeleteMapping("/{caseId}/interrogations/{interrogationId}/application-files/{fileId}")
    public ResponseEntity<Void> deleteApplicationFile(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            @PathVariable Long fileId,
            Authentication authentication
    ) {
        log.info("Deleting application file {} for interrogation: {}, case: {}", fileId, interrogationId, caseId);
        caseInterrogationService.deleteApplicationFile(caseId, interrogationId, fileId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
