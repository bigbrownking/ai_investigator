package org.di.digital.service.impl.osmotr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.OsmotrProcessingMessage;
import org.di.digital.dto.request.osmotr.DistributionRequest;
import org.di.digital.dto.request.osmotr.OsmotrDecisionDto;
import org.di.digital.dto.request.osmotr.OsmotrSubmitDecisionsRequest;
import org.di.digital.dto.response.osmotr.OsmotrDataItemDto;
import org.di.digital.dto.response.osmotr.OsmotrResultDto;
import org.di.digital.dto.response.osmotr.OsmotrResultSegmentDto;
import org.di.digital.dto.response.osmotr.OsmotrSubmitDecisionsResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.OsmotrProcessingStatus;
import org.di.digital.model.osmotr.OsmotrResult;
import org.di.digital.model.osmotr.OsmotrResultSegment;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.osmotr.OsmotrResultRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.core.MinioService;
import org.di.digital.service.osmotr.OsmotrService;
import org.di.digital.service.impl.queue.OsmotrQueueService;
import org.di.digital.util.Mapper;
import org.di.digital.util.PdfSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

import static java.util.Base64.getDecoder;
import static java.util.Base64.getEncoder;
import static org.di.digital.util.requests.RequestUrlBuilder.osmotrDecisionUrl;
import static org.di.digital.util.requests.UserUtil.validateUserAccess;

@Slf4j
@Service
@RequiredArgsConstructor
public class OsmotrServiceImpl implements OsmotrService {

    private final WebClient.Builder webClientBuilder;
    private final MinioService minioService;
    private final OsmotrResultRepository osmotrResultRepository;
    private final UserRepository userRepository;
    private final CaseRepository caseRepository;
    private final OsmotrQueueService osmotrQueueService;
    private final PdfSplitter pdfSplitter;
    private final Mapper mapper;

    @Value("${model.host}")
    private String osmotrHost;

    @Value("${osmotr.port}")
    private String osmotrPort;

    @Transactional
    public OsmotrResultDto submitDocument(String caseNumber, String userEmail, MultipartFile file) throws Exception {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));

        String originalFileName = file.getOriginalFilename();
        byte[] fileBytes = file.getBytes();

        String storedName = UUID.randomUUID() + "_" + originalFileName;
        String originalFileUrl = minioService.uploadOsmotrFile(fileBytes, caseNumber, storedName, "original");
        log.info("Uploaded original osmotr file: {}", originalFileUrl);

        OsmotrResult result = osmotrResultRepository.findFirstByCaseNumber(caseNumber)
                .orElse(null);

        if (result != null) {

            if (result.getOriginalFileUrl() != null) {
                try {
                    minioService.deleteFile(result.getOriginalFileUrl());
                    log.info("Deleted old osmotr file: {}", result.getOriginalFileUrl());
                } catch (Exception e) {
                    log.warn("Failed to delete old osmotr file: {}", result.getOriginalFileUrl(), e);
                }
            }
            result.getSegments().forEach(segment -> {
                if (segment.getFileUrl() != null) {
                    try {
                        minioService.deleteFile(segment.getFileUrl());
                        log.info("Deleted old segment file: {}", segment.getFileUrl());
                    } catch (Exception e) {
                        log.warn("Failed to delete segment file: {}", segment.getFileUrl(), e);
                    }
                }
            });
            for (String type : List.of("report", "evidence", "return")) {
                String generatedPath = String.format("%s/osmotr/%s/%s.docx", caseNumber, type, type);
                minioService.deleteFile(generatedPath);
            }

            result.setOriginalFileName(originalFileName);
            result.setOriginalFileUrl(originalFileUrl);
            result.setStatus(OsmotrProcessingStatus.PENDING);
            result.setSessionId(null);
            result.setReportFile(null);
            result.setReportTxt(null);
            result.setErrorMessage(null);
            result.setProcessingDurationSeconds(null);
            result.setCreatedAt(LocalDateTime.now());
            result.getSegments().clear();
        } else {
            result = OsmotrResult.builder()
                    .caseNumber(caseNumber)
                    .originalFileName(originalFileName)
                    .originalFileUrl(originalFileUrl)
                    .userEmail(userEmail)
                    .status(OsmotrProcessingStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .segments(new ArrayList<>())
                    .build();
        }

        OsmotrResult saved = osmotrResultRepository.save(result);

        osmotrQueueService.sendOsmotrForProcessing(OsmotrProcessingMessage.builder()
                .fileId(saved.getId())
                .caseNumber(caseNumber)
                .originalFileName(originalFileName)
                .fileUrl(originalFileUrl)
                .userEmail(userEmail)
                .userId(user.getId())
                .build());

        return mapper.toOsmotrResultDto(saved);
    }

    @Transactional(readOnly = true)
    public List<OsmotrResultDto> getResultsByCaseNumber(String caseNumber, String email) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));
        validateUserAccess(caseEntity, user);

        return osmotrResultRepository.findByCaseNumber(caseNumber).stream()
                .map(result -> {
                    OsmotrResultDto dto = mapper.toOsmotrResultDto(result);
                    attachReportBase64(result, dto);
                    return dto;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<OsmotrResultDto> getResult(String caseNumber, Long resultId, String email) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));
        validateUserAccess(caseEntity, user);

        return osmotrResultRepository.findById(resultId)
                .map(result -> {
                    OsmotrResultDto dto = mapper.toOsmotrResultDto(result);
                    attachReportBase64(result, dto);
                    return dto;
                });
    }

    private void attachReportBase64(OsmotrResult result, OsmotrResultDto dto) {
        if (result.getReportFile() == null) return;
        try (InputStream is = minioService.downloadFile(result.getReportFile())) {
            dto.setReportFileBase64(getEncoder().encodeToString(is.readAllBytes()));
        } catch (Exception e) {
            log.error("Failed to read report docx for result {}: {}", result.getId(), e.getMessage(), e);
        }
    }

    @Transactional
    public OsmotrResultDto updateDistribution(String caseNumber, Long resultId,
                                              DistributionRequest request, String email) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));
        validateUserAccess(caseEntity, user);

        OsmotrResult result = osmotrResultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalStateException("OsmotrResult не найден: " + resultId));

        Set<Long> evidenceIds = request.getEvidenceSegmentIds() != null
                ? new HashSet<>(request.getEvidenceSegmentIds()) : Set.of();
        Set<Long> returnIds = request.getReturnSegmentIds() != null
                ? new HashSet<>(request.getReturnSegmentIds()) : Set.of();

        result.getSegments().forEach(s -> {
            s.setEvidenceNeeded(evidenceIds.contains(s.getId()));
            s.setReturnNeeded(returnIds.contains(s.getId()));
        });

        OsmotrResult saved = osmotrResultRepository.save(result);

        List<OsmotrDataItemDto> resultItems = saved.getSegments().stream()
                .map(s -> OsmotrDataItemDto.builder()
                        .docId(s.getTitle())
                        .startPage(s.getStartPage())
                        .endPage(s.getEndPage())
                        .inspectionText(s.getInspectionText())
                        .needed(s.getEvidenceNeeded())
                        .build())
                .toList();

        List<OsmotrDecisionDto> decisions = saved.getSegments().stream()
                .map(s -> OsmotrDecisionDto.builder()
                        .docId(s.getTitle())
                        .needed(s.getEvidenceNeeded())
                        .build())
                .toList();

        OsmotrResultDto dto = mapper.toOsmotrResultDto(saved);

        try {
            OsmotrSubmitDecisionsResponse response = webClientBuilder.build()
                    .post()
                    .uri(osmotrDecisionUrl(osmotrHost, osmotrPort))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(OsmotrSubmitDecisionsRequest.builder()
                            .sessionId(saved.getSessionId())
                            .userId(user.getId())
                            .results(resultItems)
                            .data(resultItems)
                            .decisions(decisions)
                            .build())
                    .retrieve()
                    .bodyToMono(OsmotrSubmitDecisionsResponse.class)
                    .block();

            if (response != null && response.getFiles() != null) {
                String evidenceB64 = response.getFiles().get("evidence_base64");
                if (evidenceB64 != null && !evidenceB64.isBlank()) {
                    overwriteGeneratedFile(caseNumber, "evidence", "evidence.docx", evidenceB64);
                }

                String returnB64 = response.getFiles().get("return_base64");
                if (returnB64 != null && !returnB64.isBlank()) {
                    overwriteGeneratedFile(caseNumber, "return", "return.docx", returnB64);
                }
            }
        } catch (Exception e) {
            log.error("Failed to submit decisions to AI for resultId={}: {}", resultId, e.getMessage(), e);
        }

        if (saved.getReportFile() != null) {
            try (InputStream is = minioService.downloadFile(saved.getReportFile())) {
                dto.setReportFileBase64(getEncoder().encodeToString(is.readAllBytes()));
            } catch (Exception e) {
                log.error("Failed to read report docx for result {}: {}", resultId, e.getMessage(), e);
            }
        }

        return dto;
    }

    public byte[] downloadSegment(String caseNumber, Long resultId, Long segmentId, String email) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));
        validateUserAccess(caseEntity, user);

        OsmotrResult result = osmotrResultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalStateException("OsmotrResult не найден: " + resultId));

        OsmotrResultSegment segment = result.getSegments().stream()
                .filter(s -> s.getId().equals(segmentId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Сегмент не найден: " + segmentId));

        try (InputStream is = minioService.downloadFile(segment.getFileUrl())) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Ошибка скачивания сегмента: " + segmentId, e);
        }
    }

    public byte[] mergeSegments(String caseNumber, Long resultId, String type, String email) throws Exception {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));
        validateUserAccess(caseEntity, user);

        OsmotrResult result = osmotrResultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalStateException("OsmotrResult не найден: " + resultId));

        List<OsmotrResultSegment> segments = result.getSegments().stream()
                .filter(s -> "EVIDENCE".equals(type)
                        ? Boolean.TRUE.equals(s.getEvidenceNeeded())
                        : Boolean.TRUE.equals(s.getReturnNeeded()))
                .sorted(Comparator.comparing(OsmotrResultSegment::getStartPage))
                .toList();

        if (segments.isEmpty()) {
            throw new IllegalStateException("Нет сегментов с type=" + type);
        }

        return pdfSplitter.mergeSegments(segments.stream()
                .map(s -> {
                    try (InputStream is = minioService.downloadFile(s.getFileUrl())) {
                        return is.readAllBytes();
                    } catch (Exception e) {
                        throw new IllegalStateException("Ошибка скачивания сегмента: " + s.getId(), e);
                    }
                }).toList());
    }

    public byte[] downloadGeneratedFile(String caseNumber, Long resultId, String fileType, String email) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));
        validateUserAccess(caseEntity, user);

        OsmotrResult result = osmotrResultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalStateException("Осмотр не найден: " + resultId));

        if (result.getSessionId() == null) {
            throw new IllegalStateException("Осмотр ещё не обработан: " + resultId);
        }

        String fileName = fileType + ".docx";
        String objectPath = String.format("%s/osmotr/%s/%s", caseNumber, fileType, fileName);

        if (minioService.fileExists(objectPath)) {
            try (InputStream is = minioService.downloadFile(objectPath)) {
                log.info("Generated file found in MinIO: {}", objectPath);
                return is.readAllBytes();
            } catch (Exception e) {
                log.error("Failed to read generated file {}: {}", objectPath, e.getMessage(), e);
                throw new IllegalStateException("Ошибка чтения файла: " + fileName, e);
            }
        }

        throw new IllegalStateException(
                "Файл '" + fileType + "' не был сгенерирован для этого осмотра: " + objectPath);
    }

    @Transactional(readOnly = true)
    public List<OsmotrResultDto> searchSegments(String caseNumber, String query, String email) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));
        validateUserAccess(caseEntity, user);

        if (query == null || query.isBlank()) {
            return List.of();
        }

        String q = query.trim().toLowerCase();

        return osmotrResultRepository.searchBySegmentText(caseNumber, q).stream()
                .map(result -> {
                    OsmotrResultDto dto = mapper.toOsmotrResultDto(result);
                    if (dto.getSegments() != null) {
                        List<OsmotrResultSegmentDto> matched = dto.getSegments().stream()
                                .filter(s -> s.getInspectionText() != null
                                        && s.getInspectionText().toLowerCase().contains(q))
                                .toList();
                        dto.setSegments(matched);
                    }
                    return dto;
                })
                .toList();
    }

    private void overwriteGeneratedFile(String caseNumber, String type, String fileName, String base64) {
        try {
            byte[] bytes = getDecoder().decode(base64);
            String url = minioService.uploadOsmotrGeneratedFile(bytes, caseNumber, fileName, type);
            log.info("Stored generated {} file in MinIO: {}", type, url);
        } catch (Exception e) {
            log.error("Failed to store generated {} file for case {}: {}", type, caseNumber, e.getMessage(), e);
        }
    }
}