package org.di.digital.service.report;

import org.di.digital.dto.message.ReportResultMessage;
import org.di.digital.model.report.CaseReview;
import org.springframework.core.io.Resource;

public interface ReportService {
    Resource generateReport(String caseNumber, String userEmail);
    void saveProcessing(ReportResultMessage message);
    void saveCompleted(ReportResultMessage message);
    void saveFailed(ReportResultMessage message);
    CaseReview getByCaseNumber(String caseNumber);
    Resource downloadReport(String caseNumber, String userEmail);
}