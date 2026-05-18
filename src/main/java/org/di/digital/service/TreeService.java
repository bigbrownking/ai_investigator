package org.di.digital.service;

import org.di.digital.dto.response.TreeDataResponse;
import org.di.digital.dto.response.TreeModuleResponse;
import org.di.digital.model.enums.TreeModuleType;

import java.util.List;

public interface TreeService {

    /**
     * Загрузить ВСЕ модули для дела из внешнего API и сохранить в БД
     */
    TreeDataResponse fetchAndSaveAllModules(Long caseId, String userEmail);

    /**
     * Загрузить КОНКРЕТНЫЙ модуль для дела
     */
    TreeModuleResponse fetchAndSaveModule(Long caseId, TreeModuleType moduleType, String userEmail);

    /**
     * Получить все сохраненные модули для дела (последние версии)
     */
    TreeDataResponse getLatestModules(Long caseId, String userEmail);

    /**
     * Получить конкретный модуль (последняя версия)
     */
    TreeModuleResponse getLatestModule(Long caseId, TreeModuleType moduleType, String userEmail);

    /**
     * Получить историю версий модуля
     */
    List<TreeModuleResponse> getModuleHistory(Long caseId, TreeModuleType moduleType, String userEmail);

    /**
     * Обновить данные (перезагрузить из API)
     */
    TreeDataResponse refreshAllModules(Long caseId, String userEmail);

    /**
     * Удалить старые версии (cleanup)
     */
    void cleanupOldVersions(Long caseId, int keepVersions);
}
