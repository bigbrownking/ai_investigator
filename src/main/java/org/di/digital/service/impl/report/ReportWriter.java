package org.di.digital.service.impl.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.ReportProcessingMessage;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.model.report.CaseReview;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.review.CaseReviewRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.impl.queue.ReportQueueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportWriter {

    private final CaseReviewRepository caseReviewRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final ReportQueueService reportQueueService;

    @Transactional
    public Long queueReport(String caseNumber, String userEmail) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userEmail));

        CaseReview review = caseReviewRepository.findByCaseEntityNumber(caseNumber)
                .orElseGet(() -> CaseReview.builder().caseEntity(caseEntity).build());

        review.setUserEmail(userEmail);
        review.setStatus(CaseFileStatusEnum.PENDING);
        review.setTimestamp(LocalDateTime.now());
        review.setErrorMessage(null);
        review.setCompletedAt(null);
        review = caseReviewRepository.save(review);

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