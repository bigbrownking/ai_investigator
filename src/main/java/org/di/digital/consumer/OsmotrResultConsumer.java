package org.di.digital.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.OsmotrResultMessage;
import org.di.digital.dto.response.osmotr.OsmotrDataItemDto;
import org.di.digital.dto.response.osmotr.OsmotrReportResponse;
import org.di.digital.model.enums.OsmotrProcessingStatus;
import org.di.digital.model.osmotr.OsmotrResult;
import org.di.digital.model.osmotr.OsmotrResultSegment;
import org.di.digital.repository.osmotr.OsmotrResultRepository;
import org.di.digital.service.core.MinioService;
import org.di.digital.service.impl.core.NotificationService;
import org.di.digital.util.PdfSplitter;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static java.util.Base64.getDecoder;

@Slf4j
@Component
@RequiredArgsConstructor
public class OsmotrResultConsumer {
    private final NotificationService notificationService;
    private final OsmotrResultRepository osmotrResultRepository;
    private final MinioService minioService;
    private final PdfSplitter pdfSplitter;

    @RabbitListener(queues = "${spring.rabbitmq.osmotr.result.queue}")
    public void consume(OsmotrResultMessage message) {
        log.info("Received osmotr result: fileId={}, status={}", message.getFileId(), message.getStatus());

        try {
            notificationService.notifyOsmotrStatus(message);

            if (OsmotrProcessingStatus.COMPLETED.equals(message.getStatus())
                    || OsmotrProcessingStatus.FAILED.equals(message.getStatus())) {
                handleResult(message);
            }
        } catch (Exception e) {
            log.error("Error handling osmotr result for fileId={}: {}", message.getFileId(), e.getMessage(), e);
        }
    }

    @Transactional
    public void handleResult(OsmotrResultMessage message) {
        OsmotrResult result = osmotrResultRepository.findById(message.getFileId())
                .orElseThrow(() -> new IllegalStateException("OsmotrResult not found: " + message.getFileId()));

        result.setSessionId(message.getSessionId());
        result.setStatus(message.getStatus());
        result.setProcessingDurationSeconds(message.getProcessingDurationSeconds());

        if (OsmotrProcessingStatus.FAILED.equals(message.getStatus()) || message.getResult() == null) {
            result.setErrorMessage(message.getErrorMessage());
            osmotrResultRepository.save(result);
            log.info("OsmotrResult {} finished with status {}", message.getFileId(), message.getStatus());
            return;
        }

        OsmotrReportResponse report = message.getResult();
        log.info("Report for {}: status={}, hasBase64={}, base64Len={}, reportTxtLen={}, dataItems={}",
                message.getFileId(),
                message.getStatus(),
                report != null && report.getReportFileBase64() != null && !report.getReportFileBase64().isBlank(),
                report != null && report.getReportFileBase64() != null ? report.getReportFileBase64().length() : 0,
                report != null && report.getReportTxt() != null ? report.getReportTxt().length() : 0,
                report != null && report.getData() != null ? report.getData().size() : 0);
        result.setReportTxt(report.getReportTxt());

        if (report.getReportFileBase64() != null && !report.getReportFileBase64().isBlank()) {
            try {
                byte[] reportBytes = getDecoder().decode(report.getReportFileBase64());
                String reportUrl = minioService.uploadOsmotrGeneratedFile(
                        reportBytes, result.getCaseNumber(), "report.docx", "report");
                result.setReportFile(reportUrl);
                log.info("Report docx cached in MinIO: {}", reportUrl);
            } catch (Exception e) {
                log.error("Failed to store report docx for result {}: {}", message.getFileId(), e.getMessage(), e);
            }
        }

        if (report.getData() != null && !report.getData().isEmpty()) {
            try {
                byte[] originalBytes = minioService.downloadFile(result.getOriginalFileUrl()).readAllBytes();
                result.getSegments().clear();

                for (OsmotrDataItemDto item : report.getData()) {
                    try {
                        byte[] segmentBytes = pdfSplitter.extractPages(originalBytes, item.getStartPage(), item.getEndPage());
                        String segmentFileName = UUID.randomUUID() + "_" + sanitize(item.getDocId()) + ".pdf";
                        String segmentUrl = minioService.uploadOsmotrFile(segmentBytes, result.getCaseNumber(), segmentFileName, "segments");

                        result.addSegment(OsmotrResultSegment.builder()
                                .title(item.getDocId())
                                .startPage(item.getStartPage())
                                .endPage(item.getEndPage())
                                .inspectionText(item.getInspectionText())
                                .evidenceNeeded(item.getNeeded())
                                .returnNeeded(false)
                                .fileUrl(segmentUrl)
                                .osmotrResult(result)
                                .build());

                        log.info("Segment created: {} pages {}-{} → {}", item.getDocId(), item.getStartPage(), item.getEndPage(), segmentUrl);
                    } catch (Exception e) {
                        log.error("Failed to create segment for docId={}: {}", item.getDocId(), e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to split PDF for result {}: {}", message.getFileId(), e.getMessage(), e);
                result.setErrorMessage("PDF split failed: " + e.getMessage());
            }
        }

        osmotrResultRepository.save(result);
        log.info("OsmotrResult {} saved with {} segments", message.getFileId(), result.getSegments().size());
    }

    private String sanitize(String name) {
        if (name == null) return "segment";
        return name.replaceAll("[^a-zA-Zа-яА-Я0-9._-]", "_").toLowerCase();
    }
}
