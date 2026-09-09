package org.di.digital.service.impl.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.ReportProcessingMessage;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.file.CaseFileStatusEnum;
import org.di.digital.model.report.CaseReport;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.review.CaseReportRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.impl.queue.ReportQueueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.di.digital.util.requests.UserUtil.getCurrentLang;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportWriter {

    private final CaseReportRepository caseReportRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final ReportQueueService reportQueueService;

    @Transactional
    public Long queueReport(String caseNumber, String userEmail) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(getCurrentLang(), caseNumber)));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(getCurrentLang(), userEmail)));

        CaseReport review = caseReportRepository.findByCaseEntityNumber(caseNumber)
                .orElseGet(() -> CaseReport.builder().caseEntity(caseEntity).build());

        review.setUserEmail(userEmail);
        review.setStatus(CaseFileStatusEnum.PENDING);
        review.setTimestamp(LocalDateTime.now());
        review.setErrorMessage(null);
        review.setCompletedAt(null);
        review = caseReportRepository.save(review);

        reportQueueService.sendReportForProcessing(ReportProcessingMessage.builder()
                .caseNumber(caseNumber)
                .userId(user.getId())
                .userEmail(userEmail)
                .build());

        log.info("Report generation queued for case {} by {}, reviewId={}",
                caseNumber, userEmail, review.getId());

        return review.getId();
    }
}