package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.queue.TaskQueue;
import org.di.digital.model.enums.file.TaskStatus;
import org.di.digital.security.crypto.FileCipher;
import org.di.digital.service.core.MinioObjectStorage;
import org.di.digital.service.impl.core.DevService;
import org.di.digital.service.impl.queue.TaskQueueService;
import org.di.digital.util.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/dev")
public class DevController {

    private final DevService devService;
    private final PlanMigrationService planMigrationService;
    private final QualificationMigrationService qualificationMigrationService;
    private final IndictmentMigrationService indictmentMigrationService;
    private final FileOwnerMigrationService fileOwnerMigrationService;
    private final InterrogationOwnerMigrationService interrogationOwnerMigrationService;
    private final CasePermissionMigrationService casePermissionMigrationService;
    private final TaskQueueService taskQueueService;
    private final FileCipher fileCipher;
    private final MinioObjectStorage storage;

    // ─── Stats ────────────────────────────────────────────────────

    @GetMapping("/queue/stats")
    public ResponseEntity<DevService.QueueStatsResponse> getStats() {
        return ResponseEntity.ok(devService.getQueueStats());
    }

    // ─── Queue view ───────────────────────────────────────────────

    @GetMapping("/queue/processing")
    public ResponseEntity<List<TaskQueue>> getProcessing() {
        return ResponseEntity.ok(devService.getProcessingTasks());
    }

    @GetMapping("/queue/pending")
    public ResponseEntity<List<TaskQueue>> getPending() {
        return ResponseEntity.ok(devService.getPendingTasks());
    }

    @GetMapping("/queue/status/{status}")
    public ResponseEntity<List<TaskQueue>> getByStatus(@PathVariable TaskStatus status) {
        return ResponseEntity.ok(devService.getTasksByStatus(status));
    }

    @GetMapping("/queue/case/{caseNumber}")
    public ResponseEntity<List<TaskQueue>> getByCase(@PathVariable String caseNumber) {
        return ResponseEntity.ok(devService.getTasksByCase(caseNumber));
    }

    @GetMapping("/queue/user/{email}")
    public ResponseEntity<List<TaskQueue>> getByUser(@PathVariable String email) {
        return ResponseEntity.ok(devService.getTasksByUser(email));
    }

    // ─── Priority ─────────────────────────────────────────────────

    @PatchMapping("/queue/case/{caseNumber}/priority")
    public ResponseEntity<Void> setCasePriority(
            @PathVariable String caseNumber,
            @RequestParam int priority
    ) {
        devService.setCasePriority(caseNumber, priority);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/queue/file/{caseFileId}/priority")
    public ResponseEntity<Void> setFilePriority(
            @PathVariable Long caseFileId,
            @RequestParam int priority
    ) {
        devService.setFilePriority(caseFileId, priority);
        return ResponseEntity.ok().build();
    }

    // ─── Control ──────────────────────────────────────────────────

    @PostMapping("/queue/file/{caseFileId}/retry")
    public ResponseEntity<Void> retryFile(@PathVariable Long caseFileId) {
        devService.retryFailedTask(caseFileId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/queue/case/{caseNumber}/retry-failed")
    public ResponseEntity<Void> retryCaseFailed(@PathVariable String caseNumber) {
        devService.retryAllFailedForCase(caseNumber);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/queue/retry-all-failed")
    public ResponseEntity<Map<String, Long>> retryAllFailed() {
        long modified = devService.retryAllFailed();
        return ResponseEntity.ok(Map.of("retried", modified));
    }

    @PostMapping("/queue/file/{caseFileId}/cancel")
    public ResponseEntity<Void> cancelFile(@PathVariable Long caseFileId) {
        devService.cancelPendingTask(caseFileId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/recalculate-pages")
    public ResponseEntity<String> recalculateAllPages() {
        devService.recalculateAllStartEndPages();
        return ResponseEntity.ok("Done");
    }
    @PostMapping("/recalculate-pages-null")
    public ResponseEntity<String> recalculatePagesForNullFiles() {
        devService.recalculatePagesForNullFiles();
        return ResponseEntity.ok("Done");
    }

    @PostMapping("/sync-figurants")
    public ResponseEntity<String> syncFigurants(){
        devService.syncAllFigurants();
        return ResponseEntity.ok("Done");
    }

    @PostMapping("/migrate-qualification")
    public ResponseEntity<String> migrate1() {
        int count = qualificationMigrationService.migrateExistingQualifications();
        return ResponseEntity.ok("Migrated " + count + " qualifications");
    }

    @PostMapping("/migrate-indictment")
    public ResponseEntity<String> migrate2() {
        int count = indictmentMigrationService.migrateExistingIndictments();
        return ResponseEntity.ok("Migrated " + count + " indictments");
    }


    @PostMapping("/migrate-plan")
    public ResponseEntity<String> migrate3() {
        int count = planMigrationService.migrateExistingPlans();
        return ResponseEntity.ok("Migrated " + count + " plans");
    }

    @PostMapping("/migrate-fileOwner")
    public ResponseEntity<FileOwnerMigrationService.FileOwnerMigrationResult> migrate4() {
        return ResponseEntity.ok(fileOwnerMigrationService.migrateFileOwners());
    }

    @PostMapping("/migrate-interrogationOwners")
    public ResponseEntity<InterrogationOwnerMigrationService.InterrogationOwnerMigrationResult> migrate5() {
        return ResponseEntity.ok(interrogationOwnerMigrationService.migrateInterrogationOwners());
    }
    @PostMapping("/migrate-casePermissions")
    public ResponseEntity<Integer> migrate6() {
        return ResponseEntity.ok(casePermissionMigrationService.grantFullAccessToAllOwners());
    }

    @GetMapping("/avg-page")
    public DevService.AvgTimePerPageResponse avgPage(){
        return devService.getAvgTimePerPage();
    }

    @PostMapping("/reconcile")
    public ResponseEntity<TaskQueueService.OrphanCleanupResult> reconcile(
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return ResponseEntity.ok(taskQueueService.reconcileOrphanedTasks(dryRun));
    }

    @PostMapping("/reset-stuck")
    public ResponseEntity<Integer> resetStuck() {
        return ResponseEntity.ok(taskQueueService.resetStuckProcessingTasks());
    }

    @PostMapping(value = "/decrypt-archive",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> decryptArchive(@RequestPart("file") MultipartFile archive) {
        StreamingResponseBody body = out -> {
            try (ZipInputStream zis = new ZipInputStream(archive.getInputStream(), StandardCharsets.UTF_8);
                 ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {

                zos.setLevel(Deflater.NO_COMPRESSION);

                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;

                    String name = entry.getName();
                    byte[] bytes = zis.readAllBytes();

                    if (fileCipher.isEncryptedName(name)) {
                        try {
                            bytes = fileCipher.decrypt(bytes);
                            name = name.substring(0, name.length() - ".enc".length());
                        } catch (Exception e) {
                            log.warn("Не удалось расшифровать {}: {}", name, e.getMessage());
                            continue;
                        }
                    }

                    zos.putNextEntry(new ZipEntry(name));
                    zos.write(bytes);
                    zos.closeEntry();
                }
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"decrypted.zip\"")
                .contentType(MediaType.valueOf("application/zip"))
                .body(body);
    }

    @GetMapping(value = "/decrypt-folder", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> decryptFolder(@RequestParam String prefix) {
        List<String> names = storage.listObjectNames(prefix);
        log.info("Decrypting {} objects under prefix {}", names.size(), prefix);

        StreamingResponseBody body = out -> {
            try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                zos.setLevel(Deflater.NO_COMPRESSION);

                for (String objectName : names) {
                    byte[] bytes;
                    try (InputStream in = storage.getObject(objectName)) {
                        bytes = in.readAllBytes();
                    } catch (Exception e) {
                        log.warn("Пропущен {}: {}", objectName, e.getMessage());
                        continue;
                    }

                    String name = objectName;
                    if (fileCipher.isEncryptedName(objectName)) {
                        try {
                            bytes = fileCipher.decrypt(bytes);
                            name = name.substring(0, name.length() - ".enc".length());
                        } catch (Exception e) {
                            log.warn("Не удалось расшифровать {}: {}", objectName, e.getMessage());
                            continue;
                        }
                    }

                    zos.putNextEntry(new ZipEntry(name));
                    zos.write(bytes);
                    zos.closeEntry();
                }
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"decrypted.zip\"")
                .contentType(MediaType.valueOf("application/zip"))
                .body(body);
    }
}