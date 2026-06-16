package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.osmotr.UpdateSegmentSelectionRequest;
import org.di.digital.model.osmotr.OsmotrResult;
import org.di.digital.repository.osmotr.OsmotrResultRepository;
import org.di.digital.service.impl.queue.OsmotrService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static java.net.URLEncoder.encode;

@Slf4j
@RestController
@RequestMapping("/cases/{caseNumber}/osmotr")
@RequiredArgsConstructor
public class OsmotrController {

    private final OsmotrService digitalOsmotrService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OsmotrResult> submit(
            @PathVariable String caseNumber,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws Exception {
        OsmotrResult result = digitalOsmotrService.submitDocument(
                caseNumber, authentication.getName(), file
        );
        return ResponseEntity.accepted().body(result);
    }

    @GetMapping
    public ResponseEntity<List<OsmotrResult>> getResults(@PathVariable String caseNumber,
                                                         Authentication authentication) {
        return ResponseEntity.ok(digitalOsmotrService.getResultsByCaseNumber(caseNumber, authentication.getName()));
    }

    @GetMapping("/{resultId}")
    public ResponseEntity<OsmotrResult> getResult(@PathVariable String caseNumber,
                                                  @PathVariable Long resultId,
                                                  Authentication authentication) {
        return digitalOsmotrService.getResult(caseNumber, resultId, authentication.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{resultId}/segments")
    public ResponseEntity<OsmotrResult> updateSegments(
            @PathVariable String caseNumber,
            @PathVariable Long resultId,
            @RequestBody UpdateSegmentSelectionRequest request,
            Authentication authentication
    ) {
        //TODO /api/submit-decisions model request
        return ResponseEntity.ok(digitalOsmotrService.updateSegmentSelection(
                caseNumber, resultId, request, authentication.getName()
        ));
    }

    @GetMapping("/{resultId}/merge")
    public ResponseEntity<byte[]> mergeSegments(
            @PathVariable String caseNumber,
            @PathVariable Long resultId,
            @RequestParam(defaultValue = "true") boolean needed,
            Authentication authentication
    ) throws Exception {
        byte[] pdf = digitalOsmotrService.mergeSegments(
                caseNumber, resultId, needed, authentication.getName()
        );

        String fileName = needed ? "вещественные_документы.pdf" : "возврат.pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" +
                                encode(fileName, StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }


    @GetMapping("/{resultId}/download")
    public ResponseEntity<byte[]> downloadGeneratedFile(
            @PathVariable String caseNumber,
            @PathVariable Long resultId,
            @RequestParam String fileType,
            Authentication authentication
    ) throws Exception {
        byte[] file = digitalOsmotrService.downloadGeneratedFile(
                caseNumber, resultId, fileType, authentication.getName()
        );

        String fileName = switch (fileType) {
            case "return" -> "возврат.docx";
            case "evidence" -> "вещественные_документы.docx";
            case "report" -> "отчет_осмотра.docx";
            default -> throw new IllegalArgumentException("Unknown file_type: " + fileType);
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" +
                                encode(fileName, StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file);
    }

}