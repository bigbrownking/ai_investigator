package org.di.digital.util;

import io.minio.*;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.queue.TaskQueue;
import org.di.digital.repository.queue.TaskQueueRepository;
import org.di.digital.security.crypto.FileCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EncryptFilesMigrationService {

    private final MinioClient minioClient;
    private final FileCipher fileCipher;
    private final JdbcTemplate jdbcTemplate;
    private final TaskQueueRepository taskQueueRepository;

    @Value("${minio.bucket.name:cases}")
    private String bucketName;

    private static final List<String[]> URL_COLUMNS = List.of(
            new String[]{"case_files", "file_url"},
            new String[]{"osmotr_results", "original_file_url"},
            new String[]{"osmotr_results", "report_file"},
            new String[]{"osmotr_result_segments", "file_url"},
            new String[]{"case_reports", "report_file_url"},
            new String[]{"case_interrogation_qa", "audio_file_url"},
            new String[]{"case_interrogation_audio_records", "audio_file_url"},
            new String[]{"case_interrogation_application_files", "file_url"},
            new String[]{"review_item_files", "file_url"},
            new String[]{"support_ticket_photos", "file_url"}
    );

    public EncryptFilesResult migrate() {
        if (!fileCipher.isEnabled()) {
            log.warn("MinIO encryption is disabled, skipping encrypt files migration");
            return new EncryptFilesResult(0, 0, 0, 0);
        }

        ObjectsStat stat = encryptObjects();
        int dbRowsUpdated = updateUrls();

        log.info("Encrypt files migration done. total={}, encrypted={}, failed={}, dbRowsUpdated={}",
                stat.total, stat.encrypted, stat.failed, dbRowsUpdated);
        return new EncryptFilesResult(stat.total, stat.encrypted, stat.failed, dbRowsUpdated);
    }

    private ObjectsStat encryptObjects() {
        int total = 0;
        int encrypted = 0;
        int failed = 0;
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .recursive(true)
                    .build());
            for (Result<Item> result : results) {
                String objectName = result.get().objectName();
                total++;
                if (fileCipher.isEncryptedName(objectName)) {
                    continue;
                }
                try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build())) {
                    byte[] plain = in.readAllBytes();
                    byte[] storedBytes = fileCipher.encrypt(plain);
                    minioClient.putObject(PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName + ".enc")
                            .stream(new ByteArrayInputStream(storedBytes), storedBytes.length, -1)
                            .build());
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
                    encrypted++;
                    log.info("Encrypted object: {}", objectName);
                } catch (Exception e) {
                    failed++;
                    log.error("Failed to encrypt object: {}", objectName, e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to list objects in bucket {}", bucketName, e);
        }
        return new ObjectsStat(total, encrypted, failed);
    }

    private int updateUrls() {
        int updated = 0;
        for (String[] col : URL_COLUMNS) {
            String table = col[0];
            String column = col[1];
            updated += jdbcTemplate.update(
                    "UPDATE " + table + " SET " + column + " = " + column + " || '.enc' " +
                    "WHERE " + column + " IS NOT NULL AND " + column + " != '' AND " + column + " NOT LIKE '%.enc'");
        }

        int mongo = 0;
        for (TaskQueue task : taskQueueRepository.findAll()) {
            String fileUrl = task.getFileUrl();
            if (fileUrl != null && !fileUrl.isEmpty() && !fileUrl.endsWith(".enc")) {
                task.setFileUrl(fileUrl + ".enc");
                taskQueueRepository.save(task);
                mongo++;
            }
        }
        log.info("Encrypt files DB URLs updated: sql={}, mongo={}", updated, mongo);
        return updated + mongo;
    }

    private record ObjectsStat(int total, int encrypted, int failed) {}

    public record EncryptFilesResult(int totalObjects, int encryptedObjects, int failedObjects, int dbRowsUpdated) {}
}