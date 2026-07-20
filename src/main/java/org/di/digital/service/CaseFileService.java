package org.di.digital.service;

import org.di.digital.dto.message.AssessmentResult;
import org.di.digital.dto.message.ClassificationResult;
import org.di.digital.model.cases.CaseFile;

public interface CaseFileService {
    CaseFile markAsCompleted(Long caseFileId, String result, Long processingDurationSeconds,
                             ClassificationResult classification, AssessmentResult assessment);
    CaseFile markAsFailed(Long caseFileId, String errorMessage);

    void markAsProcessing(Long caseFileId);

    void retryFile(Long caseId, Long caseFileId, String email);

    void setQualification(Long caseId, Long fileId, boolean isQualification, String email);
}
