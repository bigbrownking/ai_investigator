package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.Case;
import org.di.digital.model.CaseFile;
import org.di.digital.model.CaseFileStatusEnum;
import org.di.digital.repository.CaseFileRepository;
import org.di.digital.service.CaseFileService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseFileServiceImpl implements CaseFileService {
    private final CaseFileRepository caseFileRepository;
    private final TaskQueueService taskQueueService;

    @Override
    public CaseFile markAsCompleted(Long caseFileId, String result) {
        CaseFile caseFile = caseFileRepository.findById(caseFileId)
                .orElseThrow(() -> new RuntimeException("File not found: " + caseFileId));

        caseFile.setStatus(CaseFileStatusEnum.COMPLETED);
        caseFile.setCompletedAt(LocalDateTime.now());

        caseFileRepository.save(caseFile);

        taskQueueService.completeTask(caseFileId);

        log.info("File {} marked as COMPLETED", caseFileId);
        return caseFile;
    }

    @Override
    public CaseFile markAsFailed(Long caseFileId, String errorMessage) {
        CaseFile caseFile = caseFileRepository.findById(caseFileId)
                .orElseThrow(() -> new RuntimeException("File not found: " + caseFileId));

        caseFile.setStatus(CaseFileStatusEnum.FAILED);
        caseFile.setCompletedAt(LocalDateTime.now());

        caseFileRepository.save(caseFile);

        taskQueueService.failTask(caseFileId, errorMessage);

        log.info("File {} marked as FAILED", caseFileId);
        return caseFile;
    }

    @Override
    public void markAsProcessing(Long caseFileId) {
        CaseFile caseFile = caseFileRepository.findById(caseFileId)
                .orElseThrow(() -> new RuntimeException("File not found: " + caseFileId));

        caseFile.setStatus(CaseFileStatusEnum.PROCESSING);
        caseFile.setCompletedAt(LocalDateTime.now());

        caseFileRepository.save(caseFile);

        log.info("File {} marked as FAILED", caseFileId);
    }
}
