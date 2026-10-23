package org.di.digital.service.impl.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.IllegalStateMessage;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.file.CaseFileStatusEnum;
import org.di.digital.model.report.CaseReport;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.review.CaseReportRepository;
import org.di.digital.service.core.MinioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.di.digital.util.requests.RequestBodyBuilder.reportBody;
import static org.di.digital.util.requests.RequestUrlBuilder.reportGeneratorUrl;
import static org.di.digital.util.requests.UserUtil.getCurrentLang;

/**
 * Отдаёт справку по делу для ADMIN и REG_ADMIN. Если справка уже есть в БД, берёт её из MinIO.
 * Если справки в БД нет, запрашивает у ML-сервиса (POST /api/report), сохраняет в MinIO и в case_reports
 * и отдаёт пользователю.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseReportResolver {

    private static final String DEFAULT_LANGUAGE = "russian";
    private static final long ML_USER_ID = 0L;
    private final CaseFileRepository caseFileRepository;
    private final CaseReportRepository caseReportRepository;
    private final MinioService minioService;
    private final WebClient.Builder webClientBuilder;

    @Value("${model.host}")
    private String reportHost;

    @Value("${spravka.port}")
    private String reportPort;

    // REQUIRES_NEW: вызывающие методы идут в readOnly-транзакции, а здесь возможна запись в БД.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Resource getOrGenerate(Long userId, Case caseEntity) {
        Optional<CaseReport> existing = caseReportRepository.findByCaseEntityNumber(caseEntity.getNumber());
        if (existing.isPresent() && !isStale(existing.get(), caseEntity.getId())) {
            return readStored(existing.get());
        }
        // справки нет или она устарела: формируем заново у внешнего сервиса и обновляем запись в БД
        return generateAndSave(userId, caseEntity, existing.orElse(null));
    }

    /**
     * Справка устарела, если после начала её формирования завершилась обработка какого-либо файла дела
     * (в дело добавили документ, и он попал в индекс). Файлы, которые ещё обрабатываются, не учитываются:
     * когда они завершатся, справка станет устаревшей и при следующем запросе будет сформирована заново.
     */
    private boolean isStale(CaseReport report, Long caseId) {
        LocalDateTime generatedAt = report.getTimestamp() != null ? report.getTimestamp() : report.getCompletedAt();
        if (generatedAt == null) {
            return false;
        }
        LocalDateTime latestFileChange = caseFileRepository.findLatestChangeAt(caseId, CaseFileStatusEnum.COMPLETED);
        return latestFileChange != null && latestFileChange.isAfter(generatedAt);
    }

    private Resource readStored(CaseReport report) {
        if (report.getStatus() != CaseFileStatusEnum.COMPLETED) {
            throw new IllegalStateException(
                    IllegalStateMessage.INVALID_OUTPUT.localized(getCurrentLang(), report.getStatus().getLabel()));
        }
        if (report.getReportFileUrl() == null || report.getReportFileUrl().isBlank()) {
            throw new NotFoundException(NotFoundMessage.FILE.localized(getCurrentLang()));
        }
        try (InputStream stream = minioService.downloadFile(report.getReportFileUrl())) {
            return new ByteArrayResource(stream.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(getCurrentLang()));
        }
    }

    private Resource generateAndSave(Long userId, Case caseEntity, CaseReport existing) {
        String caseNumber = caseEntity.getNumber();
        String language = caseEntity.getLanguage() != null ? caseEntity.getLanguage() : DEFAULT_LANGUAGE;
        long startedAt = System.nanoTime();
        LocalDateTime startedAtTime = LocalDateTime.now();

        byte[] bytes;
        try {
            bytes = webClientBuilder.build()
                    .post()
                    .uri(reportGeneratorUrl(reportHost, reportPort))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(reportBody(caseNumber, language, ML_USER_ID, userId))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Report generation failed for case {}: {} {}", caseNumber, e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 404) {
                throw new NotFoundException(NotFoundMessage.REPORT.localized(getCurrentLang(), caseNumber));
            }
            throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(getCurrentLang()));
        } catch (WebClientRequestException e) {
            log.error("Report service is unreachable for case {}: POST {} ({})", caseNumber, reportGeneratorUrl(reportHost, reportPort), e.getMessage());
            throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(getCurrentLang()));
        }

        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(getCurrentLang()));
        }

        String fileName = String.format("справка_%s.docx", caseNumber.replace("/", "-"));
        String reportFileUrl = minioService.uploadReportFile(bytes, caseNumber, fileName);

        // обновляем существующую запись (у case_reports unique case_id), иначе создаём новую;
        // файл справки в MinIO перезаписывается под тем же именем
        CaseReport report = existing != null ? existing : CaseReport.builder().caseEntity(caseEntity).build();
        report.setFileName(fileName);
        report.setReportFileUrl(reportFileUrl);
        report.setStatus(CaseFileStatusEnum.COMPLETED);
        report.setErrorMessage(null);
        report.setProcessingDurationSeconds((System.nanoTime() - startedAt) / 1_000_000_000L);
        report.setTimestamp(startedAtTime);   // начало формирования: с ним сравнивается время обработки файлов
        report.setCompletedAt(LocalDateTime.now());
        caseReportRepository.save(report);

        log.info("Report for case {} {} via ML service and saved, url={}", caseNumber,
                existing != null ? "regenerated" : "generated", reportFileUrl);
        return new ByteArrayResource(bytes);
    }
}
