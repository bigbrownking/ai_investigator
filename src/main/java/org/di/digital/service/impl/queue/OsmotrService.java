package org.di.digital.service.impl.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.OsmotrProcessingMessage;
import org.di.digital.dto.request.osmotr.UpdateSegmentSelectionRequest;
import org.di.digital.dto.response.osmotr.OsmotrDataItemDto;
import org.di.digital.dto.response.osmotr.OsmotrReportResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.OsmotrProcessingStatus;
import org.di.digital.model.osmotr.OsmotrResult;
import org.di.digital.model.osmotr.OsmotrResultSegment;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.osmotr.OsmotrResultRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.MinioService;
import org.di.digital.util.PdfSplitter;
import org.di.digital.util.requests.RequestUrlBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.*;

import static org.di.digital.util.requests.RequestUrlBuilder.osmotrDownloadUrl;
import static org.di.digital.util.requests.UserUtil.validateUserAccess;

@Slf4j
@Service
@RequiredArgsConstructor
public class OsmotrService {

    private final WebClient.Builder webClientBuilder;
    private final MinioService minioService;
    private final OsmotrResultRepository osmotrResultRepository;
    private final UserRepository userRepository;
    private final CaseRepository caseRepository;
    private final RabbitTemplate rabbitTemplate;
    private final PdfSplitter pdfSplitter;

    @Value("${model.host}")
    private String osmotrHost;

    @Value("${osmotr.port}")
    private String osmotrPort;

    @Value("${spring.rabbitmq.osmotr.exchange}")
    private String osmotrExchange;

    @Value("${spring.rabbitmq.osmotr.routing-key}")
    private String osmotrRoutingKey;

    @Transactional
    public OsmotrResult submitDocument(String caseNumber, String userEmail, MultipartFile file) throws Exception {
        String originalFileName = file.getOriginalFilename();
        byte[] fileBytes = file.getBytes();

        String storedName = UUID.randomUUID() + "_" + originalFileName;
        String originalFileUrl = minioService.uploadOsmotrFile(fileBytes, caseNumber, storedName, "original");
        log.info("Uploaded original osmotr file: {}", originalFileUrl);

        OsmotrResult result = OsmotrResult.builder()
                .caseNumber(caseNumber)
                .originalFileName(originalFileName)
                .originalFileUrl(originalFileUrl)
                .userEmail(userEmail)
                .status(OsmotrProcessingStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .segments(new ArrayList<>())
                .build();
        OsmotrResult saved = osmotrResultRepository.save(result);

        OsmotrProcessingMessage message = OsmotrProcessingMessage.builder()
                .fileId(saved.getId())
                .caseNumber(caseNumber)
                .originalFileName(originalFileName)
                .fileUrl(originalFileUrl)
                .userEmail(userEmail)
                .build();

        rabbitTemplate.convertAndSend(osmotrExchange, osmotrRoutingKey, message);
        log.info("Sent osmotr processing message for file {} in case {}", originalFileName, caseNumber);

        return saved;
    }

    @Transactional
    public void handleResult(Long resultId,
                             String sessionId,
                             OsmotrReportResponse report,
                             OsmotrProcessingStatus status,
                             String errorMessage,
                             Long duration) {

        OsmotrResult result = osmotrResultRepository.findById(resultId)
                .orElseThrow(() -> new RuntimeException("OsmotrResult not found: " + resultId));

        result.setSessionId(sessionId);
        result.setStatus(status);
        result.setProcessingDurationSeconds(duration);

        if (OsmotrProcessingStatus.FAILED.equals(status) || report == null) {
            result.setErrorMessage(errorMessage);

            log.info(
                    "OsmotrResult {} finished with status {}",
                    resultId,
                    status
            );
            return;
        }

        result.setReportFile(report.getReportFile());
        result.setReportTxt(report.getReportTxt());

        if (report.getData() != null && !report.getData().isEmpty()) {
            try {
                byte[] originalBytes = minioService
                        .downloadFile(result.getOriginalFileUrl())
                        .readAllBytes();

                result.getSegments().clear();

                for (OsmotrDataItemDto item : report.getData()) {
                    try {

                        byte[] segmentBytes = pdfSplitter.extractPages(
                                originalBytes,
                                item.getStartPage(),
                                item.getEndPage()
                        );

                        String segmentFileName = UUID.randomUUID()
                                + "_"
                                + sanitize(item.getDocId())
                                + ".pdf";

                        String segmentUrl = minioService.uploadOsmotrFile(
                                segmentBytes,
                                result.getCaseNumber(),
                                segmentFileName,
                                "segments"
                        );

                        OsmotrResultSegment segment = OsmotrResultSegment.builder()
                                .docId(item.getDocId())
                                .startPage(item.getStartPage())
                                .endPage(item.getEndPage())
                                .inspectionText(item.getInspectionText())
                                .needed(item.getNeeded())
                                .fileUrl(segmentUrl)
                                .osmotrResult(result)
                                .build();

                        result.addSegment(segment);
                        log.info(
                                "Segment created: {} pages {}-{} → {}",
                                item.getDocId(),
                                item.getStartPage(),
                                item.getEndPage(),
                                segmentUrl
                        );

                    } catch (Exception e) {
                        log.error(
                                "Failed to create segment for docId={}: {}",
                                item.getDocId(),
                                e.getMessage(),
                                e
                        );
                    }
                }

            } catch (Exception e) {
                log.error(
                        "Failed to split PDF for result {}: {}",
                        resultId,
                        e.getMessage(),
                        e
                );

                result.setErrorMessage(
                        "PDF split failed: " + e.getMessage()
                );
            }
        }

        log.info(
                "OsmotrResult {} saved with {} segments",
                resultId,
                result.getSegments().size()
        );
    }

    @Transactional(readOnly = true)
    public List<OsmotrResult> getResultsByCaseNumber(String caseNumber, String email) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseNumber));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        validateUserAccess(caseEntity, user);
        return osmotrResultRepository.findByCaseNumber(caseNumber);
    }

    @Transactional(readOnly = true)
    public Optional<OsmotrResult> getResult(String caseNumber, Long resultId, String email) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseNumber));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        validateUserAccess(caseEntity, user);
        return osmotrResultRepository.findById(resultId);
    }

    @Transactional
    public OsmotrResult updateSegmentSelection(String caseNumber, Long resultId,
                                               UpdateSegmentSelectionRequest request, String email) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseNumber));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));
        validateUserAccess(caseEntity, user);

        OsmotrResult result = osmotrResultRepository.findById(resultId)
                .orElseThrow(() -> new RuntimeException("OsmotrResult не найден: " + resultId));

        if (request.getSegmentIds() == null) {
            result.getSegments().forEach(s -> s.setNeeded(Boolean.TRUE.equals(request.getNeeded())));
        } else {
            result.getSegments().stream()
                    .filter(s -> request.getSegmentIds().contains(s.getId()))
                    .forEach(s -> s.setNeeded(Boolean.TRUE.equals(request.getNeeded())));
        }

        return osmotrResultRepository.save(result);
    }

    public byte[] mergeSegments(String caseNumber, Long resultId, boolean needed, String email) throws Exception {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseNumber));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));
        validateUserAccess(caseEntity, user);

        OsmotrResult result = osmotrResultRepository.findById(resultId)
                .orElseThrow(() -> new RuntimeException("OsmotrResult не найден: " + resultId));

        List<OsmotrResultSegment> segments = result.getSegments().stream()
                .filter(s -> Boolean.valueOf(needed).equals(s.getNeeded()))
                .sorted(Comparator.comparing(OsmotrResultSegment::getStartPage))
                .toList();

        if (segments.isEmpty()) {
            throw new IllegalStateException("Нет сегментов с needed=" + needed);
        }

        return pdfSplitter.mergeSegments(segments.stream()
                .map(s -> {
                    try {
                        return minioService.downloadFile(s.getFileUrl()).readAllBytes();
                    } catch (Exception e) {
                        throw new RuntimeException("Ошибка скачивания сегмента: " + s.getId(), e);
                    }
                })
                .toList());
    }
    public byte[] downloadGeneratedFile(String caseNumber, Long resultId, String fileType, String email) throws Exception {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseNumber));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));
        validateUserAccess(caseEntity, user);

        OsmotrResult result = osmotrResultRepository.findById(resultId)
                .orElseThrow(() -> new RuntimeException("OsmotrResult не найден: " + resultId));

        return webClientBuilder.build()
                .get()
                .uri(osmotrDownloadUrl(osmotrHost, osmotrPort, fileType, result.getSessionId()))
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }
    private String sanitize(String name) {
        if (name == null) return "segment";
        return name.replaceAll("[^a-zA-Zа-яА-Я0-9._-]", "_").toLowerCase();
    }
}

