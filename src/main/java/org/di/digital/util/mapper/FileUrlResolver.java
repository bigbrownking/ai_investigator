package org.di.digital.util.mapper;

import lombok.RequiredArgsConstructor;
import org.di.digital.service.core.MinioService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileUrlResolver {

    private final MinioService minioService;

    public String preview(String fileUrl) {
        return fileUrl != null ? minioService.generatePresignedUrlForPreview(fileUrl) : null;
    }

    public String download(String fileUrl, String originalFileName) {
        return fileUrl != null
                ? minioService.generatePresignedUrlForDownload(fileUrl, originalFileName)
                : null;
    }
}