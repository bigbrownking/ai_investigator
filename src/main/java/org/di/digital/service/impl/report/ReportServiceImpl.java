package org.di.digital.service.impl.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.ReportProcessingMessage;
import org.di.digital.dto.message.ReportResultMessage;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.model.report.CaseReview;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.review.CaseReviewRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.LogService;
import org.di.digital.service.core.MinioService;
import org.di.digital.service.impl.queue.ReportQueueService;
import org.di.digital.service.report.ReportService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final CaseReviewRepository caseReviewRepository;
    private final CaseRepository caseRepository;
    private final MinioService minioService;
    private final LogService logService;
    private final ReportWriter reportWriter;
    private final ReportAwaitRegistry awaitRegistry;
    private static final long REPORT_TIMEOUT_MINUTES = 5;
    @Override
    public Resource generateReport(String caseNumber, String userEmail) {
        Long reviewId = reportWriter.queueReport(caseNumber, userEmail);

        CompletableFuture<ReportAwaitRegistry.ReportOutcome> future =
                awaitRegistry.register(reviewId);

        try {
            ReportAwaitRegistry.ReportOutcome outcome =
                    future.get(REPORT_TIMEOUT_MINUTES, TimeUnit.MINUTES);

            if (!outcome.success()) {
                throw new IllegalStateException(
                        "Не удалось сгенерировать отчёт: " + outcome.errorMessage());
            }

            logService.log(
                    String.format("Generated report by %s user in case %s", userEmail, caseNumber),
                    LogLevel.INFO, LogAction.REPORT_DOWNLOAD, caseNumber, userEmail);

            return fetchFromMinio(outcome.reportFileUrl());

        } catch (TimeoutException e) {
            awaitRegistry.cancel(reviewId);
            throw new IllegalStateException(
                    "Превышено время ожидания генерации отчёта для дела: " + caseNumber);
        } catch (InterruptedException e) {
            awaitRegistry.cancel(reviewId);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Генерация отчёта прервана");
        } catch (ExecutionException e) {
            awaitRegistry.cancel(reviewId);
            throw new IllegalStateException("Ошибка генерации отчёта", e);
        }
    }

    private Resource fetchFromMinio(String reportFileUrl) {
        try (InputStream stream = minioService.downloadFile(reportFileUrl)) {
            return new ByteArrayResource(stream.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать файл отчёта", e);
        }
    }

    @Override
    @Transactional
    public void saveProcessing(ReportResultMessage message) {
        CaseReview review = getOrCreate(message);
        review.setStatus(CaseFileStatusEnum.PROCESSING);
        caseReviewRepository.save(review);
        log.info("Review for case {} -> PROCESSING", message.getCaseNumber());
    }

    @Override
    @Transactional
    public void saveCompleted(ReportResultMessage message) {
        CaseReview review = getOrCreate(message);
        review.setStatus(CaseFileStatusEnum.COMPLETED);
        review.setReportFileUrl(message.getReportFileUrl());
        review.setFileName(message.getFileName());
        review.setProcessingDurationSeconds(message.getProcessingDurationSeconds());
        review.setCompletedAt(LocalDateTime.now());
        review.setErrorMessage(null);
        caseReviewRepository.save(review);

        awaitRegistry.complete(review.getId(), new ReportAwaitRegistry.ReportOutcome(
                true, message.getReportFileUrl(), null));

        log.info("Review for case {} -> COMPLETED, url={}",
                message.getCaseNumber(), message.getReportFileUrl());
    }


    @Override
    @Transactional
    public void saveFailed(ReportResultMessage message) {
        CaseReview review = getOrCreate(message);
        review.setStatus(CaseFileStatusEnum.FAILED);
        review.setErrorMessage(message.getErrorMessage());
        review.setCompletedAt(LocalDateTime.now());
        caseReviewRepository.save(review);

        awaitRegistry.complete(review.getId(), new ReportAwaitRegistry.ReportOutcome(
                false, null, message.getErrorMessage()));

        log.error("Review for case {} -> FAILED: {}",
                message.getCaseNumber(), message.getErrorMessage());
    }

    @Override
    @Transactional(readOnly = true)
    public CaseReview getByCaseNumber(String caseNumber) {
        return caseReviewRepository.findByCaseEntityNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException(
                        "Отчёт не найден для дела: " + caseNumber));
    }

    private CaseReview getOrCreate(ReportResultMessage message) {
        return caseReviewRepository.findByCaseEntityNumber(message.getCaseNumber())
                .orElseGet(() -> buildNew(message));
    }

    private CaseReview buildNew(ReportResultMessage message) {
        Case caseEntity = caseRepository.findByNumber(message.getCaseNumber())
                .orElseThrow(() -> new IllegalStateException(
                        "Дело не найдено: " + message.getCaseNumber()));
        return CaseReview.builder()
                .caseEntity(caseEntity)
                .fileName(message.getFileName())
                .userEmail(message.getUserEmail())
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadReport(String caseNumber, String userEmail) {
        CaseReview review = caseReviewRepository.findByCaseEntityNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException(
                        "Отчёт не найден для дела: " + caseNumber));

        if (review.getStatus() != CaseFileStatusEnum.COMPLETED) {
            throw new IllegalStateException(
                    "Отчёт ещё не готов, текущий статус: " + review.getStatus());
        }
        if (review.getReportFileUrl() == null || review.getReportFileUrl().isBlank()) {
            throw new IllegalStateException("Файл отчёта отсутствует для дела: " + caseNumber);
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
            throw new IllegalStateException("Не удалось прочитать файл отчёта", e);
        }
    }
}