package org.di.digital.service.impl.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.ReportResultMessage;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.IllegalStateMessage;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.file.CaseFileStatusEnum;
import org.di.digital.model.enums.log.LogAction;
import org.di.digital.model.enums.log.LogLevel;

import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.report.CaseReport;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.review.CaseReportRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.LogService;
import org.di.digital.service.cases.CaseAccessService;
import org.di.digital.service.core.MinioService;
import org.di.digital.service.report.ReportService;
import org.di.digital.util.requests.UserUtil;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.di.digital.util.requests.UserUtil.getCurrentLang;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final CaseReportRepository caseReportRepository;
    private final CaseRepository caseRepository;
    private final MinioService minioService;
    private final LogService logService;
    private final ReportWriter reportWriter;
    private final ReportAwaitRegistry awaitRegistry;
    private final UserRepository userRepository;
    private final UserUtil userUtil;
    private final CaseAccessService caseAccessService;
    private static final long REPORT_TIMEOUT_MINUTES = 5;

    @Override
    public Resource generateReport(String caseNumber, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), userEmail)));
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));

        userUtil.validateUserAccess(caseEntity, user);
        caseAccessService.require(caseEntity, user, CaseModule.REPORT, CaseAction.ADD);

        Long reviewId = reportWriter.queueReport(caseNumber, userEmail);

        CompletableFuture<ReportAwaitRegistry.ReportOutcome> future = awaitRegistry.register(reviewId);

        try {
            ReportAwaitRegistry.ReportOutcome outcome = future.get(REPORT_TIMEOUT_MINUTES, TimeUnit.MINUTES);

            if (!outcome.success()) {
                throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang()));
            }

            logService.log(
                    String.format("Generated report by %s user in case %s", userEmail, caseNumber),
                    LogLevel.INFO, LogAction.REPORT_DOWNLOAD, caseNumber, userEmail);

            return fetchFromMinio(outcome.reportFileUrl());

        } catch (TimeoutException e) {
            awaitRegistry.cancel(reviewId);
            throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang(), caseNumber));
        } catch (InterruptedException e) {
            awaitRegistry.cancel(reviewId);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang()));
        } catch (ExecutionException e) {
            awaitRegistry.cancel(reviewId);
            throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang()));
        }
    }

    private Resource fetchFromMinio(String reportFileUrl) {
        try (InputStream stream = minioService.downloadFile(reportFileUrl)) {
            return new ByteArrayResource(stream.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang()));
        }
    }

    @Override
    @Transactional
    public void saveProcessing(ReportResultMessage message) {
        CaseReport review = getOrCreate(message);
        review.setStatus(CaseFileStatusEnum.PROCESSING);
        caseReportRepository.save(review);
        log.info("Review for case {} -> PROCESSING", message.getCaseNumber());
    }

    @Override
    @Transactional
    public void saveCompleted(ReportResultMessage message) {
        CaseReport review = getOrCreate(message);
        review.setStatus(CaseFileStatusEnum.COMPLETED);
        review.setReportFileUrl(message.getReportFileUrl());
        review.setFileName(message.getFileName());
        review.setProcessingDurationSeconds(message.getProcessingDurationSeconds());
        review.setCompletedAt(LocalDateTime.now());
        review.setErrorMessage(null);
        caseReportRepository.save(review);

        awaitRegistry.complete(review.getId(), new ReportAwaitRegistry.ReportOutcome(
                true, message.getReportFileUrl(), null));

        log.info("Review for case {} -> COMPLETED, url={}",
                message.getCaseNumber(), message.getReportFileUrl());
    }

    @Override
    @Transactional
    public void saveFailed(ReportResultMessage message) {
        CaseReport review = getOrCreate(message);
        review.setStatus(CaseFileStatusEnum.FAILED);
        review.setErrorMessage(message.getErrorMessage());
        review.setCompletedAt(LocalDateTime.now());
        caseReportRepository.save(review);

        awaitRegistry.complete(review.getId(), new ReportAwaitRegistry.ReportOutcome(
                false, null, message.getErrorMessage()));

        log.error("Review for case {} -> FAILED: {}",
                message.getCaseNumber(), message.getErrorMessage());
    }

    @Override
    @Transactional(readOnly = true)
    public CaseReport getByCaseNumber(String caseNumber) {
        return caseReportRepository.findByCaseEntityNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.REPORT.localized(currentLang(), caseNumber)));

    }

    private CaseReport getOrCreate(ReportResultMessage message) {
        return caseReportRepository.findByCaseEntityNumber(message.getCaseNumber())
                .orElseGet(() -> buildNew(message));
    }

    private CaseReport buildNew(ReportResultMessage message) {
        Case caseEntity = caseRepository.findByNumber(message.getCaseNumber())
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), message.getCaseNumber())));

        return CaseReport.builder()
                .caseEntity(caseEntity)
                .fileName(message.getFileName())
                .userEmail(message.getUserEmail())
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadReport(String caseNumber, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), userEmail)));
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));

        userUtil.validateUserAccess(caseEntity, user);
        caseAccessService.require(caseEntity, user, CaseModule.REPORT, CaseAction.DOWNLOAD);

        CaseReport review = caseReportRepository.findByCaseEntityNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.REPORT.localized(currentLang(), caseNumber)));


        if (review.getStatus() != CaseFileStatusEnum.COMPLETED) {
            throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang(), review.getStatus().getLabel()));
        }
        if (review.getReportFileUrl() == null || review.getReportFileUrl().isBlank()) {
            throw new NotFoundException(NotFoundMessage.FILE.localized(currentLang()));
        }

        logService.log(
                String.format("Downloading report by %s user in case %s", userEmail, caseNumber),
                LogLevel.INFO,
                LogAction.REPORT_DOWNLOAD,
                caseNumber,
                userEmail);

        try (InputStream stream = minioService.downloadFile(review.getReportFileUrl())) {
            return new ByteArrayResource(stream.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang()));
        }
    }

    private UserSettingsLanguage currentLang() {
        return getCurrentLang();
    }
}