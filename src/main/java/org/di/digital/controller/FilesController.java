package org.di.digital.controller;

import java.io.InputStream;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.di.digital.service.core.MinioService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FilesController {

    private final MinioService minioService;

    private static final Map<String, MediaType> CONTENT_TYPES = Map.of(
            "pdf", MediaType.APPLICATION_PDF,
            "doc", MediaType.parseMediaType("application/msword"),
            "docx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            "xls", MediaType.APPLICATION_OCTET_STREAM,
            "xlsx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            "mp3", MediaType.parseMediaType("audio/mpeg"),
            "wav", MediaType.parseMediaType("audio/wav"),
            "mp4", MediaType.parseMediaType("video/mp4")
    );

    @GetMapping("/preview")
    public ResponseEntity<InputStreamResource> preview(
            @RequestParam("path") String objectPath,
            Authentication authentication) {
        InputStream in = minioService.downloadFile(objectPath);
        MediaType mediaType = detectContentType(objectPath);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new InputStreamResource(in));
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(
            @RequestParam("path") String objectPath,
            @RequestParam("name") String filename,
            Authentication authentication) {
        InputStream in = minioService.downloadFile(objectPath);
        MediaType mediaType = detectContentType(filename);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(new InputStreamResource(in));
    }

    private MediaType detectContentType(String path) {
        String lower = path.toLowerCase();
        String ext;
        if (lower.endsWith(".enc")) {
            String withoutEnc = lower.substring(0, lower.length() - 4);
            int dot = withoutEnc.lastIndexOf('.');
            ext = dot != -1 ? withoutEnc.substring(dot + 1) : "";
        } else {
            int dot = lower.lastIndexOf('.');
            ext = dot != -1 ? lower.substring(dot + 1) : "";
        }
        return CONTENT_TYPES.getOrDefault(ext, MediaType.APPLICATION_OCTET_STREAM);
    }
}
