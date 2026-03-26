package org.di.digital.service;

import org.di.digital.model.CaseFile;

public interface CaseFileService {
    CaseFile markAsCompleted(Long caseFileId, String result, Long processingDurationSeconds);
    CaseFile markAsFailed(Long caseFileId, String errorMessage);
    void markAsProcessing(Long caseFileId);
}
