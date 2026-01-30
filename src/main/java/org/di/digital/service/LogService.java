package org.di.digital.service;

import org.di.digital.model.Case;
import org.di.digital.model.Log;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;

public interface LogService {
    void log(String description, LogLevel level, LogAction action, Case caseEntity);
    Page<Log> allLogs(int page, int size);
    Page<Log> searchLogs(LogLevel level, LogAction action,
                         LocalDateTime startDate, LocalDateTime endDate,
                         String caseNUmber, String username,
                         int page, int size);

    void deleteOldLogs(LocalDateTime beforeDate);
}
