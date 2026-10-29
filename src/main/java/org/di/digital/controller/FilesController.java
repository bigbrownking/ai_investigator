package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import org.di.digital.model.file.FileAccessToken;
import org.di.digital.service.core.FileTokenService;
import org.di.digital.service.core.MinioService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FilesController {

    private final MinioService minioService;
    private final FileTokenService fileTokenService;

    private static final Map<String, MediaType> CONTENT_TYPES = Map.of(
            "pdf", MediaType.APPLICATION_PDF,
            "doc", MediaType.parseMediaType("application/msword"),
            "docx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            "xls", MediaType.parseMediaType("application/vnd.ms-excel"),
            "xlsx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            "mp3", MediaType.parseMediaType("audio/mpeg"),
            "wav", MediaType.parseMediaType("audio/wav"),
            "mp4", MediaType.parseMediaType("video/mp4")
    );

    @GetMapping("/token/{token}")
    public ResponseEntity<InputStreamResource> serveByToken(@PathVariable String token) {
        FileAccessToken fileToken = fileTokenService.resolveToken(token);

        InputStream in = minioService.downloadFile(fileToken.getObjectName());

        String displayName = fileToken.getOriginalFileName();
        MediaType mediaType = resolveMediaType(fileToken.getContentType(), displayName);

        ContentDisposition disposition = (fileToken.isInline()
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(displayName, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(in));
    }

    private MediaType resolveMediaType(String contentType, String fileName) {
        if (contentType != null && !contentType.isBlank()) {
            try {
                return MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {}
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".enc")) {
            lower = lower.substring(0, lower.length() - 4);
        }
        int dot = lower.lastIndexOf('.');
        String ext = dot != -1 ? lower.substring(dot + 1) : "";
        return CONTENT_TYPES.getOrDefault(ext, MediaType.APPLICATION_OCTET_STREAM);
    }
}