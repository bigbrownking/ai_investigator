package org.di.digital.service.impl.core;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.service.core.MinioObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioObjectStorageImpl implements MinioObjectStorage {

    private final MinioClient minioClient;

    @Value("${minio.bucket.name:cases}")
    private String bucketName;

    @Value("${minio.url}")
    private String minioUrl;

    @Value("${minio.public.url}")
    private String minioPublicUrl;

    @Value("${minio.presigned.url.expiry.hours:24}")
    private int presignedUrlExpiryHours;

    @Override
    public String putObject(String objectName, InputStream stream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(stream, size, -1)
                    .contentType(contentType)
                    .build());
            return bucketName + "/" + objectName;
        } catch (Exception e) {
            log.error("Error uploading object: {}", objectName, e);
            throw new IllegalStateException("Failed to upload object: " + objectName, e);
        }
    }

    @Override
    public InputStream getObject(String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("Error downloading object: {}", objectName, e);
            throw new IllegalStateException("Failed to download object", e);
        }
    }

    @Override
    public void removeObject(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
            log.info("Object deleted: {}", objectName);
        } catch (Exception e) {
            log.error("Error deleting object: {}", objectName, e);
        }
    }

    @Override
    public boolean exists(String objectName) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            log.error("Error checking object: {}", objectName, e);
            return false;
        } catch (Exception e) {
            log.error("Error checking object: {}", objectName, e);
            return false;
        }
    }

    @Override
    public List<String> listObjectNames(String prefix) {
        List<String> names = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .recursive(true)
                    .build());
            for (Result<Item> result : results) {
                names.add(result.get().objectName());
            }
        } catch (Exception e) {
            log.error("Error listing objects: {}", prefix, e);
            throw new IllegalStateException("Failed to list objects", e);
        }
        return names;
    }

    @Override
    public void removeObjects(List<String> objectNames) {
        try {
            List<DeleteObject> toDelete = objectNames.stream().map(DeleteObject::new).toList();
            Iterable<Result<DeleteError>> errors = minioClient.removeObjects(RemoveObjectsArgs.builder()
                    .bucket(bucketName)
                    .objects(toDelete)
                    .build());
            for (Result<DeleteError> error : errors) {
                log.warn("Delete error: {}", error.get().message());
            }
        } catch (Exception e) {
            log.error("Error removing objects", e);
        }
    }

    @Override
    public String presignedGetUrl(String objectName, Map<String, String> headers) {
        try {
            String presignedUrl = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectName)
                    .expiry(presignedUrlExpiryHours, TimeUnit.HOURS)
                    .extraQueryParams(headers)
                    .build());
            return toPublicUrl(presignedUrl);
        } catch (Exception e) {
            log.error("Error generating presigned URL for: {}", objectName, e);
            throw new IllegalStateException("Failed to generate presigned URL", e);
        }
    }

    private String toPublicUrl(String presignedUrl) {
        if (minioPublicUrl != null && !minioPublicUrl.isBlank()) {
            return presignedUrl.replace(minioUrl, minioPublicUrl);
        }
        return presignedUrl;
    }

    public String extractObjectNameFromPath(String objectPath) {
        if (objectPath == null || objectPath.isEmpty()) {
            throw new IllegalArgumentException("Object path cannot be null or empty");
        }
        if (objectPath.startsWith("http://") || objectPath.startsWith("https://")) {
            int bucketIndex = objectPath.indexOf("/" + bucketName + "/");
            if (bucketIndex != -1) {
                return objectPath.substring(bucketIndex + bucketName.length() + 2);
            }
        }
        if (objectPath.startsWith(bucketName + "/")) {
            return objectPath.substring(bucketName.length() + 1);
        }
        return objectPath;
    }
}