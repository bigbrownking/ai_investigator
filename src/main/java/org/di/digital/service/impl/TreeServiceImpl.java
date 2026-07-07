package org.di.digital.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.tree.TreeDataResponse;
import org.di.digital.dto.response.tree.TreeModuleResponse;
import org.di.digital.model.TreeData;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.TreeModuleType;
import org.di.digital.model.user.User;
import org.di.digital.repository.TreeDataRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.TreeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.di.digital.util.requests.RequestUrlBuilder.buildModuleUrl;

@Slf4j
@Service
@RequiredArgsConstructor
public class TreeServiceImpl implements TreeService {

    private final TreeDataRepository treeDataRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${model.host}")
    private String pythonHost;


    @Value("${tree.port}")
    private String treePort;
    @Override
    @Transactional
    public TreeDataResponse fetchAndSaveAllModules(String caseNumber, String userEmail) {
        log.info("Fetching all tree modules for case: {}, user: {}", caseNumber, userEmail);

        Case caseEntity = validateAndGetCase(caseNumber, userEmail);
        List<TreeModuleResponse> modules = new ArrayList<>();

        for (TreeModuleType moduleType : TreeModuleType.values()) {
            try {
                TreeModuleResponse module = fetchAndSaveModuleInternal(caseEntity, moduleType);
                modules.add(module);
            } catch (Exception e) {
                log.error("Failed to fetch module: {} for case: {}", moduleType, caseNumber, e);
                // Сохраняем информацию об ошибке
                saveErrorModule(caseEntity, moduleType, e.getMessage());
            }
        }

        log.info("Successfully fetched {} out of {} modules for case: {}",
                modules.size(), TreeModuleType.values().length, caseNumber);

        return TreeDataResponse.builder()
                .caseId(caseEntity.getId())
                .caseNumber(caseEntity.getNumber())
                .modules(modules)
                .fetchedAt(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional
    public TreeModuleResponse fetchAndSaveModule(String caseNumber, TreeModuleType moduleType, String userEmail) {
        log.info("Fetching tree module: {} for case: {}, user: {}", moduleType, caseNumber, userEmail);

        Case caseEntity = validateAndGetCase(caseNumber, userEmail);
        return fetchAndSaveModuleInternal(caseEntity, moduleType);
    }

    @Override
    @Transactional(readOnly = true)
    public TreeDataResponse getLatestModules(String caseNumber, String userEmail) {
        log.info("Getting latest tree modules for case: {}, user: {}", caseNumber, userEmail);

        Case caseEntity = validateAndGetCase(caseNumber, userEmail);
        List<TreeData> latestModules = treeDataRepository.findLatestModulesByCaseEntity(caseEntity);

        List<TreeModuleResponse> modules = latestModules.stream()
                .map(this::mapToModuleResponse)
                .collect(Collectors.toList());

        return TreeDataResponse.builder()
                .caseId(caseEntity.getId())
                .caseNumber(caseEntity.getNumber())
                .modules(modules)
                .fetchedAt(latestModules.isEmpty() ? null : latestModules.get(0).getFetchedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TreeModuleResponse getLatestModule(String caseNumber, TreeModuleType moduleType, String userEmail) {
        log.info("Getting latest tree module: {} for case: {}, user: {}", moduleType, caseNumber, userEmail);

        Case caseEntity = validateAndGetCase(caseNumber, userEmail);
        TreeData treeData = treeDataRepository
                .findFirstByCaseEntityAndModuleTypeOrderByVersionDesc(caseEntity, moduleType)
                .orElseThrow(() -> new IllegalStateException(
                        String.format("Module %s not found for case %s", moduleType, caseNumber)));

        return mapToModuleResponse(treeData);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TreeModuleResponse> getModuleHistory(String caseNumber, TreeModuleType moduleType, String userEmail) {
        log.info("Getting history for module: {} of case: {}, user: {}", moduleType, caseNumber, userEmail);

        Case caseEntity = validateAndGetCase(caseNumber, userEmail);
        List<TreeData> history = treeDataRepository
                .findByCaseEntityAndModuleTypeOrderByVersionDesc(caseEntity, moduleType);

        return history.stream()
                .map(this::mapToModuleResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TreeDataResponse refreshAllModules(String caseNumber, String userEmail) {
        log.info("Refreshing all tree modules for case: {}, user: {}", caseNumber, userEmail);
        return fetchAndSaveAllModules(caseNumber, userEmail);
    }

    @Override
    @Transactional
    public void cleanupOldVersions(String caseNumber, int keepVersions) {
        log.info("Cleaning up old versions for case: {}, keeping: {} versions", caseNumber, keepVersions);

        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));

        for (TreeModuleType moduleType : TreeModuleType.values()) {
            treeDataRepository.deleteOldVersions(caseEntity, moduleType, keepVersions);
        }

        log.info("Cleanup completed for case: {}", caseNumber);
    }

    // ==================== PRIVATE METHODS ====================

    private TreeModuleResponse fetchAndSaveModuleInternal(Case caseEntity, TreeModuleType moduleType) {
        String url = buildModuleUrl(pythonHost, treePort, caseEntity.getNumber(), moduleType);

        log.info("Fetching module: {} from URL: {}", moduleType, url);

        try {
            String jsonResponse = webClientBuilder.build()
                    .get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(WebClientResponseException.class, e -> {
                        log.error("API error for {}: {} - {}", moduleType, e.getStatusCode(), e.getResponseBodyAsString());
                        return Mono.error(new IllegalStateException(
                                String.format("API returned error %s: %s", e.getStatusCode(), e.getMessage())));
                    })
                    .block();

            if (jsonResponse == null) {
                throw new IllegalStateException("API returned null response");
            }

            // Валидация JSON
            validateJson(jsonResponse);

            // Сохраняем в БД
            TreeData treeData = saveTreeData(caseEntity, moduleType, jsonResponse, true, null);

            log.info("Successfully fetched and saved module: {} for case: {}", moduleType, caseEntity.getId());

            return mapToModuleResponse(treeData);

        } catch (Exception e) {
            log.error("Error fetching module: {} from URL: {} - {}", moduleType, url, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch module: " + moduleType, e);
        }
    }

    private TreeData saveTreeData(Case caseEntity, TreeModuleType moduleType,
                                    String jsonData, boolean success, String errorMessage) {

        // Получаем последнюю версию
        Integer nextVersion = treeDataRepository
                .findFirstByCaseEntityAndModuleTypeOrderByVersionDesc(caseEntity, moduleType)
                .map(TreeData::getVersion)
                .map(v -> v + 1)
                .orElse(1);

        TreeData treeData = TreeData.builder()
                .caseEntity(caseEntity)
                .moduleType(moduleType)
                .jsonData(jsonData)
                .version(nextVersion)
                .fetchedAt(LocalDateTime.now())
                .fetchSuccess(success)
                .errorMessage(errorMessage)
                .build();

        return treeDataRepository.save(treeData);
    }

    private void saveErrorModule(Case caseEntity, TreeModuleType moduleType, String errorMessage) {
        saveTreeData(caseEntity, moduleType, null, false, errorMessage);
    }

    private void validateJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalStateException("Received empty JSON response");
        }

        try {
            objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid JSON format: " + e.getMessage());
        }
    }

    private Case validateAndGetCase(String caseNumber, String userEmail) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        // Проверка прав доступа
        boolean isOwner = caseEntity.isOwner(user);
        boolean isMember = caseEntity.getUsers().contains(user);
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> "ADMIN".equals(role.getName()));

        if (!isOwner && !isMember && !isAdmin) {
            throw new AccessDeniedException("Access denied to case: " + caseNumber);
        }

        return caseEntity;
    }

    private TreeModuleResponse mapToModuleResponse(TreeData treeData) {
        return TreeModuleResponse.builder()
                .id(treeData.getId())
                .moduleType(treeData.getModuleType())
                .moduleName(treeData.getModuleType().getModuleName())
                .jsonData(treeData.getJsonData())
                .version(treeData.getVersion())
                .fetchedAt(treeData.getFetchedAt())
                .fetchSuccess(treeData.getFetchSuccess())
                .errorMessage(treeData.getErrorMessage())
                .createdDate(treeData.getCreatedDate())
                .updatedDate(treeData.getUpdatedDate())
                .build();
    }
}
