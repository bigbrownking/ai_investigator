package org.di.digital.service;

import org.di.digital.dto.response.tree.TreeDataResponse;
import org.di.digital.dto.response.tree.TreeModuleResponse;
import org.di.digital.model.enums.TreeModuleType;

import java.util.List;

public interface TreeService {

    /**
     * Загрузить ВСЕ модули для дела из внешнего API и сохранить в БД
     */
    TreeDataResponse fetchAndSaveAllModules(String caseNumber, String userEmail);

    /**
     * Загрузить КОНКРЕТНЫЙ модуль для дела
     */
    TreeModuleResponse fetchAndSaveModule(String caseNumber, TreeModuleType moduleType, String userEmail);

    /**
     * Получить все сохраненные модули для дела (последние версии)
     */
    TreeDataResponse getLatestModules(String caseNumber, String userEmail);

    /**
     * Получить конкретный модуль (последняя версия)
     */
    TreeModuleResponse getLatestModule(String caseNumber, TreeModuleType moduleType, String userEmail);

    /**
     * Получить историю версий модуля
     */
    List<TreeModuleResponse> getModuleHistory(String caseNumber, TreeModuleType moduleType, String userEmail);

    /**
     * Обновить данные (перезагрузить из API)
     */
    TreeDataResponse refreshAllModules(String caseNumber, String userEmail);

    /**
     * Удалить старые версии (cleanup)
     */
    void cleanupOldVersions(String caseNumber, int keepVersions);
}
