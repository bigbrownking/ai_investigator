package org.di.digital.service.impl;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.CaseFile;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket.name:cases}")
    private String bucketName;

    @Value("${minio.url}")
    private String minioUrl;

    @Value("${minio.presigned.url.expiry.hours:24}")
    private int presignedUrlExpiryHours;

    public CaseFile uploadFile(MultipartFile file, String folder) {
        try {
            ensureBucketExists();

            String storedFileName = generateFileName(file.getOriginalFilename());
            String objectName = folder + "/" + storedFileName;

            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(file.getContentType())
                                .build()
                );
            }

            // Store the object path instead of direct URL
            String objectPath = bucketName + "/" + objectName;

            return CaseFile.builder()
                    .originalFileName(file.getOriginalFilename())
                    .storedFileName(storedFileName)
                    .fileUrl(objectPath)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .uploadedAt(LocalDateTime.now())
                    .status(CaseFileStatusEnum.UPLOADED)
                    .build();

        } catch (Exception e) {
            log.error("Error uploading file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file", e);
        }
    }
    public String uploadAudio(MultipartFile file, String folder, String fio) {
        try {
            ensureBucketExists();

            String storedFileName = generateFileName(file.getOriginalFilename());

            String objectName = folder + "/audio/" + fio + "/" + storedFileName;

            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(file.getContentType())
                                .build()
                );
            }

            String objectPath = bucketName + "/" + objectName;

            log.info("Audio uploaded successfully for interrogation: {}, path: {}",
                    folder, objectPath);

            return objectPath;

        } catch (Exception e) {
            log.error("Error uploading audio file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload audio file", e);
        }
    }

    public String generatePresignedUrlForAudio(String objectPath) {
        try {
            String objectName = extractObjectNameFromPath(objectPath);

            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(presignedUrlExpiryHours, TimeUnit.HOURS)
                            .build()
            );

            log.debug("Generated presigned audio URL for: {}", objectName);
            return presignedUrl;

        } catch (Exception e) {
            log.error("Error generating presigned audio URL for: {}", objectPath, e);
            throw new RuntimeException("Failed to generate presigned audio URL", e);
        }
    }

    public String generatePresignedUrlForPreview(String objectPath) {
        try {
            String objectName = extractObjectNameFromPath(objectPath);

            Map<String, String> responseHeaders = new HashMap<>();
            responseHeaders.put("response-content-disposition", "inline");

            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(presignedUrlExpiryHours, TimeUnit.HOURS)
                            .extraQueryParams(responseHeaders)
                            .build()
            );

            log.debug("Generated presigned preview URL for: {}", objectName);
            return presignedUrl;

        } catch (Exception e) {
            log.error("Error generating presigned preview URL for: {}", objectPath, e);
            throw new RuntimeException("Failed to generate presigned preview URL", e);
        }
    }

    public String generatePresignedUrlForDownload(String objectPath, String fileName) {
        try {
            String objectName = extractObjectNameFromPath(objectPath);

            Map<String, String> responseHeaders = new HashMap<>();
            responseHeaders.put("response-content-disposition", "attachment; filename=\"" + fileName + "\"");

            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(presignedUrlExpiryHours, TimeUnit.HOURS)
                            .extraQueryParams(responseHeaders)
                            .build()
            );

        } catch (Exception e) {
            log.error("Error generating presigned download URL for: {}", objectPath, e);
            throw new RuntimeException("Failed to generate presigned download URL", e);
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
        );

        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucketName).build()
            );
            log.info("Created bucket: {}", bucketName);
        }
    }

    private String generateFileName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID() + extension;
    }

    public void deleteFile(String objectPath) {
        try {
            String objectName = extractObjectNameFromPath(objectPath);
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            log.info("File deleted successfully: {}", objectPath);
        } catch (Exception e) {
            log.error("Error deleting file from Minio: {}", e.getMessage(), e);
        }
    }

    private String extractObjectNameFromUrl(String fileUrl) {
        return fileUrl.substring(fileUrl.indexOf(bucketName) + bucketName.length() + 1);
    }

    private String extractObjectNameFromPath(String objectPath) {
        if (objectPath == null || objectPath.isEmpty()) {
            throw new IllegalArgumentException("Object path cannot be null or empty");
        }

        if (objectPath.startsWith("http://") || objectPath.startsWith("https://")) {
            try {
                int bucketIndex = objectPath.indexOf("/" + bucketName + "/");
                if (bucketIndex != -1) {
                    return objectPath.substring(bucketIndex + bucketName.length() + 2);
                }
            } catch (Exception e) {
                log.error("Failed to extract object name from URL: {}", objectPath, e);
            }
        }

        if (objectPath.startsWith(bucketName + "/")) {
            return objectPath.substring(bucketName.length() + 1);
        }

        return objectPath;
    }
    public InputStream downloadFile(String objectPath) {
        try {
            String objectName = extractObjectNameFromPath(objectPath);
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("Error downloading file from Minio: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to download file", e);
        }
    }
}