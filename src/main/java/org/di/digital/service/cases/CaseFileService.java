package org.di.digital.service.cases;

import org.di.digital.dto.message.AssessmentResult;
import org.di.digital.dto.message.ClassificationResult;
import org.di.digital.model.cases.CaseFile;

public interface CaseFileService {
    CaseFile markAsCompleted(Long caseFileId, String result, Long processingDurationSeconds,
                             ClassificationResult classification, AssessmentResult assessment);
    CaseFile markAsFailed(Long caseFileId, String errorMessage, Long processingDurationSeconds);
    void retryFile(Long caseId, Long caseFileId, String email);
    void setQualification(Long caseId, Long fileId, boolean isQualification, String email);
}
