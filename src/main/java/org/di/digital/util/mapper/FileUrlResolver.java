package org.di.digital.util.mapper;

import lombok.RequiredArgsConstructor;
import org.di.digital.model.file.FileAccessToken;
import org.di.digital.service.core.FileTokenService;
import org.di.digital.service.core.MinioObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class FileUrlResolver {

    private final FileTokenService fileTokenService;
    private final MinioObjectStorage storage;

    @Value("${minio.public.url}")
    private String backendUrl;

    public String preview(String fileUrl, String originalFileName, String contentType) {
        if (fileUrl == null) return null;

        String objectName = storage.extractObjectNameFromPath(fileUrl);
        String token = fileTokenService.generateToken(FileAccessToken.builder()
                .objectName(objectName)
                .originalFileName(originalFileName)
                .contentType(contentType)
                .inline(true)
                .build());

        return backendUrl.replaceAll("/+$", "") + "/api/files/token/" + token;
    }

    public String download(String fileUrl, String originalFileName, String contentType) {
        if (fileUrl == null) return null;

        String objectName = storage.extractObjectNameFromPath(fileUrl);
        String token = fileTokenService.generateToken(FileAccessToken.builder()
                .objectName(objectName)
                .originalFileName(originalFileName)
                .contentType(contentType)
                .inline(false)
                .build());

        return backendUrl.replaceAll("/+$", "") + "/api/files/token/" + token;
    }
}