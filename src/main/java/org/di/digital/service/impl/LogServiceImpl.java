package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.Case;
import org.di.digital.model.Log;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.repository.LogRepository;
import org.di.digital.service.LogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.di.digital.util.UserUtil.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogServiceImpl implements LogService {
    private final LogRepository logRepository;

    @Override
    @Transactional
    public void log(String description, LogLevel level, LogAction action, Case caseEntity) {
        try {
            Log logEntry = Log.builder()
                    .description(description)
                    .level(level)
                    .action(action)
                    .caseEntity(caseEntity)
                    .user(getCurrentUser())
                    .ipAddress(getClientIpAddress(getCurrentHttpRequest()))
                    .build();

            logRepository.save(logEntry);
            log.debug("Created {} log: {} - {}", level, action, description);
        } catch (Exception e) {
            log.error("Failed to create log entry: {}", e.getMessage(), e);
        }
    }

    @Override
    public Page<Log> allLogs(int page, int size) {
        return logRepository.findRecentLogs(PageRequest.of(page, size));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Log> searchLogs(LogLevel level, LogAction action, LocalDateTime startDate,
                                LocalDateTime endDate, String caseNumber, String username,
                                int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);

            return logRepository.searchLogs(
                    level,
                    action,
                    startDate,
                    endDate,
                    caseNumber,
                    username,
                    pageable
            );
        } catch (Exception e) {
            log.error("Failed to search logs: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search logs", e);
        }
    }

    @Override
    @Transactional
    public void deleteOldLogs(LocalDateTime beforeDate) {
        try {
            logRepository.deleteByTimestampBefore(beforeDate);
            log.info("Deleted logs older than {}", beforeDate);
        } catch (Exception e) {
            log.error("Failed to delete old logs: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete old logs", e);
        }
    }

}