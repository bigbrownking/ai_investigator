package org.di.digital.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.TreeDataResponse;
import org.di.digital.dto.response.TreeModuleResponse;
import org.di.digital.model.enums.TreeModuleType;
import org.di.digital.service.TreeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/tree")
@Tag(name = "Tree Data API", description = "Управление древовидными данными уголовных дел")
public class TreeController {

    private final TreeService treeService;

    @PostMapping("/cases/{caseId}/fetch-all")
    @Operation(summary = "Загрузить все модули дела из внешнего API")
    public ResponseEntity<TreeDataResponse> fetchAllModules(
            @Parameter(description = "ID уголовного дела") @PathVariable Long caseId,
            Authentication authentication
    ) {
        log.info("Fetching all tree modules for case: {}, user: {}", caseId, authentication.getName());

        TreeDataResponse response = treeService.fetchAndSaveAllModules(caseId, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/cases/{caseId}/fetch/{moduleType}")
    @Operation(summary = "Загрузить конкретный модуль дела")
    public ResponseEntity<TreeModuleResponse> fetchModule(
            @Parameter(description = "ID уголовного дела") @PathVariable Long caseId,
            @Parameter(description = "Тип модуля") @PathVariable TreeModuleType moduleType,
            Authentication authentication
    ) {
        log.info("Fetching tree module: {} for case: {}, user: {}", moduleType, caseId, authentication.getName());

        TreeModuleResponse response = treeService.fetchAndSaveModule(caseId, moduleType, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/cases/{caseId}")
    @Operation(summary = "Получить все сохраненные модули дела (последние версии)")
    public ResponseEntity<TreeDataResponse> getLatestModules(
            @Parameter(description = "ID уголовного дела") @PathVariable Long caseId,
            Authentication authentication
    ) {
        log.info("Getting latest tree modules for case: {}, user: {}", caseId, authentication.getName());

        TreeDataResponse response = treeService.getLatestModules(caseId, authentication.getName());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/cases/{caseId}/{moduleType}")
    @Operation(summary = "Получить конкретный модуль (последняя версия)")
    public ResponseEntity<TreeModuleResponse> getLatestModule(
            @Parameter(description = "ID уголовного дела") @PathVariable Long caseId,
            @Parameter(description = "Тип модуля") @PathVariable TreeModuleType moduleType,
            Authentication authentication
    ) {
        log.info("Getting latest tree module: {} for case: {}, user: {}", moduleType, caseId, authentication.getName());

        TreeModuleResponse response = treeService.getLatestModule(caseId, moduleType, authentication.getName());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/cases/{caseId}/{moduleType}/history")
    @Operation(summary = "Получить историю версий модуля")
    public ResponseEntity<List<TreeModuleResponse>> getModuleHistory(
            @Parameter(description = "ID уголовного дела") @PathVariable Long caseId,
            @Parameter(description = "Тип модуля") @PathVariable TreeModuleType moduleType,
            Authentication authentication
    ) {
        log.info("Getting history for module: {} of case: {}, user: {}", moduleType, caseId, authentication.getName());

        List<TreeModuleResponse> response = treeService.getModuleHistory(caseId, moduleType, authentication.getName());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/cases/{caseId}/refresh")
    @Operation(summary = "Обновить все модули (перезагрузить из API)")
    public ResponseEntity<TreeDataResponse> refreshAllModules(
            @Parameter(description = "ID уголовного дела") @PathVariable Long caseId,
            Authentication authentication
    ) {
        log.info("Refreshing all tree modules for case: {}, user: {}", caseId, authentication.getName());

        TreeDataResponse response = treeService.refreshAllModules(caseId, authentication.getName());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/cases/{caseId}/cleanup")
    @Operation(summary = "Удалить старые версии (оставить последние N)")
    public ResponseEntity<Void> cleanupOldVersions(
            @Parameter(description = "ID уголовного дела") @PathVariable Long caseId,
            @Parameter(description = "Количество версий для сохранения") @RequestParam(defaultValue = "5") int keepVersions,
            Authentication authentication
    ) {
        log.info("Cleaning up old versions for case: {}, keep: {}, user: {}", caseId, keepVersions, authentication.getName());

        treeService.cleanupOldVersions(caseId, keepVersions);

        return ResponseEntity.noContent().build();
    }
}
