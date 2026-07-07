package org.di.digital.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.tree.TreeDataResponse;
import org.di.digital.dto.response.tree.TreeModuleResponse;
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

    @PostMapping("/cases/{caseNumber}/fetch-all")
    @Operation(summary = "Загрузить все модули дела из внешнего API")
    public ResponseEntity<TreeDataResponse> fetchAllModules(
            @Parameter(description = "Номер уголовного дела") @PathVariable String caseNumber,
            Authentication authentication
    ) {
        log.info("Fetching all tree modules for case: {}, user: {}", caseNumber, authentication.getName());

        TreeDataResponse response = treeService.fetchAndSaveAllModules(caseNumber, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/cases/{caseNumber}/fetch/{moduleType}")
    @Operation(summary = "Загрузить конкретный модуль дела")
    public ResponseEntity<TreeModuleResponse> fetchModule(
            @Parameter(description = "Номер уголовного дела") @PathVariable String caseNumber,
            @Parameter(description = "Тип модуля") @PathVariable TreeModuleType moduleType,
            Authentication authentication
    ) {
        log.info("Fetching tree module: {} for case: {}, user: {}", moduleType, caseNumber, authentication.getName());

        TreeModuleResponse response = treeService.fetchAndSaveModule(caseNumber, moduleType, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/cases/{caseNumber}")
    @Operation(summary = "Получить все сохраненные модули дела (последние версии)")
    public ResponseEntity<TreeDataResponse> getLatestModules(
            @Parameter(description = "Номер уголовного дела") @PathVariable String caseNumber,
            Authentication authentication
    ) {
        log.info("Getting latest tree modules for case: {}, user: {}", caseNumber, authentication.getName());

        TreeDataResponse response = treeService.getLatestModules(caseNumber, authentication.getName());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/cases/{caseNumber}/{moduleType}")
    @Operation(summary = "Получить конкретный модуль (последняя версия)")
    public ResponseEntity<TreeModuleResponse> getLatestModule(
            @Parameter(description = "Номер уголовного дела") @PathVariable String caseNumber,
            @Parameter(description = "Тип модуля") @PathVariable TreeModuleType moduleType,
            Authentication authentication
    ) {
        log.info("Getting latest tree module: {} for case: {}, user: {}", moduleType, caseNumber, authentication.getName());

        TreeModuleResponse response = treeService.getLatestModule(caseNumber, moduleType, authentication.getName());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/cases/{caseNumber}/{moduleType}/history")
    @Operation(summary = "Получить историю версий модуля")
    public ResponseEntity<List<TreeModuleResponse>> getModuleHistory(
            @Parameter(description = "Номер уголовного дела") @PathVariable String caseNumber,
            @Parameter(description = "Тип модуля") @PathVariable TreeModuleType moduleType,
            Authentication authentication
    ) {
        log.info("Getting history for module: {} of case: {}, user: {}", moduleType, caseNumber, authentication.getName());

        List<TreeModuleResponse> response = treeService.getModuleHistory(caseNumber, moduleType, authentication.getName());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/cases/{caseNumber}/refresh")
    @Operation(summary = "Обновить все модули (перезагрузить из API)")
    public ResponseEntity<TreeDataResponse> refreshAllModules(
            @Parameter(description = "Номер уголовного дела") @PathVariable String caseNumber,
            Authentication authentication
    ) {
        log.info("Refreshing all tree modules for case: {}, user: {}", caseNumber, authentication.getName());

        TreeDataResponse response = treeService.refreshAllModules(caseNumber, authentication.getName());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/cases/{caseNumber}/cleanup")
    @Operation(summary = "Удалить старые версии (оставить последние N)")
    public ResponseEntity<Void> cleanupOldVersions(
            @Parameter(description = "Номер уголовного дела") @PathVariable String caseNumber,
            @Parameter(description = "Количество версий для сохранения") @RequestParam(defaultValue = "5") int keepVersions,
            Authentication authentication
    ) {
        log.info("Cleaning up old versions for case: {}, keep: {}, user: {}", caseNumber, keepVersions, authentication.getName());

        treeService.cleanupOldVersions(caseNumber, keepVersions);

        return ResponseEntity.noContent().build();
    }
}
