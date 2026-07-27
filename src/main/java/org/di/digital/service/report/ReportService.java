package org.di.digital.service.report;

import org.di.digital.dto.message.ReportResultMessage;
import org.di.digital.model.enums.ReportRejectionReason;
import org.di.digital.model.report.CaseReport;
import org.springframework.core.io.Resource;

public interface ReportService {
    Resource generateReport(String caseNumber, String userEmail);
    void saveProcessing(ReportResultMessage message);
    void saveCompleted(ReportResultMessage message);
    void saveFailed(ReportResultMessage message);
    CaseReport getByCaseNumber(String caseNumber);
    Resource downloadReport(String caseNumber, String userEmail);
    CaseReport updateCaseReport(String caseNumber, ReportRejectionReason reason);
}