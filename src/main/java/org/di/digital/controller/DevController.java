package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.queue.TaskQueue;
import org.di.digital.model.enums.TaskStatus;
import org.di.digital.service.impl.core.DevService;
import org.di.digital.util.IndictmentMigrationService;
import org.di.digital.util.PlanMigrationService;
import org.di.digital.util.QualificationMigrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/dev")
public class DevController {

    private final DevService devService;
    private final PlanMigrationService planMigrationService;
    private final QualificationMigrationService qualificationMigrationService;
    private final IndictmentMigrationService indictmentMigrationService;

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

    @GetMapping("/avg-page")
    public DevService.AvgTimePerPageResponse avgPage(){
        return devService.getAvgTimePerPage();
    }

}