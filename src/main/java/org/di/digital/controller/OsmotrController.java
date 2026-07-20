package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.osmotr.DistributionRequest;
import org.di.digital.dto.response.osmotr.OsmotrResultDto;
import org.di.digital.dto.response.osmotr.OsmotrResultSegmentDto;
import org.di.digital.model.enums.OsmotrFileType;
import org.di.digital.service.OsmotrService;
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
    public ResponseEntity<OsmotrResultDto> submit(
            @PathVariable String caseNumber,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws Exception {
        return ResponseEntity.accepted().body(
                digitalOsmotrService.submitDocument(caseNumber, authentication.getName(), file));
    }

    @GetMapping
    public ResponseEntity<List<OsmotrResultDto>> getResults(
            @PathVariable String caseNumber,
            Authentication authentication) {
        return ResponseEntity.ok(
                digitalOsmotrService.getResultsByCaseNumber(caseNumber, authentication.getName()));
    }

    @GetMapping("/{resultId}")
    public ResponseEntity<OsmotrResultDto> getResult(
            @PathVariable String caseNumber,
            @PathVariable Long resultId,
            Authentication authentication) {
        return digitalOsmotrService.getResult(caseNumber, resultId, authentication.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{resultId}/distribution")
    public ResponseEntity<OsmotrResultDto> updateDistribution(
            @PathVariable String caseNumber,
            @PathVariable Long resultId,
            @RequestBody DistributionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                digitalOsmotrService.updateDistribution(caseNumber, resultId, request, authentication.getName()));
    }

    @GetMapping("/{resultId}/segments/{segmentId}/download")
    public ResponseEntity<byte[]> downloadSegment(
            @PathVariable String caseNumber,
            @PathVariable Long resultId,
            @PathVariable Long segmentId,
            Authentication authentication) {
        byte[] pdf = digitalOsmotrService.downloadSegment(caseNumber, resultId, segmentId, authentication.getName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encode("документ.pdf", StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/{resultId}/merge")
    public ResponseEntity<byte[]> mergeSegments(
            @PathVariable String caseNumber,
            @PathVariable Long resultId,
            @RequestParam String type,
            Authentication authentication) throws Exception {
        byte[] pdf = digitalOsmotrService.mergeSegments(caseNumber, resultId, type, authentication.getName());
        String fileName = "EVIDENCE".equals(type) ? "вещественные_документы.pdf" : "возврат.pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encode(fileName, StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }


    @GetMapping("/{resultId}/download")
    public ResponseEntity<byte[]> downloadGeneratedFile(
            @PathVariable String caseNumber,
            @PathVariable Long resultId,
            @RequestParam OsmotrFileType fileType,
            Authentication authentication) {
        byte[] file = digitalOsmotrService.downloadGeneratedFile(
                caseNumber, resultId, fileType.getValue().toLowerCase(), authentication.getName());
        String fileName = switch (fileType) {
            case RETURN -> "возврат.docx";
            case EVIDENCE -> "вещественные_документы.docx";
            case REPORT -> "постановление.docx";
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encode(fileName, StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file);
    }

    @GetMapping("/search")
    public ResponseEntity<List<OsmotrResultDto>> searchSegments(
            @PathVariable String caseNumber,
            @RequestParam String query,
            Authentication authentication) {
        return ResponseEntity.ok(
                digitalOsmotrService.searchSegments(caseNumber, query, authentication.getName()));
    }
}