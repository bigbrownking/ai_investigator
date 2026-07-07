package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.OsmotrProcessingMessage;
import org.di.digital.dto.request.osmotr.DistributionRequest;
import org.di.digital.dto.request.osmotr.OsmotrDecisionDto;
import org.di.digital.dto.request.osmotr.OsmotrSubmitDecisionsRequest;
import org.di.digital.dto.response.osmotr.OsmotrDataItemDto;
import org.di.digital.dto.response.osmotr.OsmotrResultDto;
import org.di.digital.dto.response.osmotr.OsmotrSubmitDecisionsResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.OsmotrProcessingStatus;
import org.di.digital.model.osmotr.OsmotrResult;
import org.di.digital.model.osmotr.OsmotrResultSegment;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.osmotr.OsmotrResultRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.MinioService;
import org.di.digital.service.OsmotrService;
import org.di.digital.service.impl.queue.OsmotrQueueService;
import org.di.digital.util.Mapper;
import org.di.digital.util.PdfSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.*;

import static org.di.digital.util.requests.RequestUrlBuilder.osmotrDecisionUrl;
import static org.di.digital.util.requests.RequestUrlBuilder.osmotrDownloadUrl;
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
                .map(mapper::toOsmotrResultDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<OsmotrResultDto> getResult(String caseNumber, Long resultId, String email) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));
        validateUserAccess(caseEntity, user);
        return osmotrResultRepository.findById(resultId).stream()
                .map(mapper::toOsmotrResultDto).findFirst();
    }

    // Реализация
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

        try {
            webClientBuilder.build()
                    .post()
                    .uri(osmotrDecisionUrl(osmotrHost, osmotrPort))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(OsmotrSubmitDecisionsRequest.builder()
                            .sessionId(saved.getSessionId())
                            .userId(user.getId())
                            .results(resultItems)
                            .decisions(decisions)
                            .build())
                    .retrieve()
                    .bodyToMono(OsmotrSubmitDecisionsResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to submit decisions to AI for resultId={}: {}", resultId, e.getMessage());
        }

        return mapper.toOsmotrResultDto(saved);
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

        try {
            return minioService.downloadFile(segment.getFileUrl()).readAllBytes();
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
                    try {
                        return minioService.downloadFile(s.getFileUrl()).readAllBytes();
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

        return webClientBuilder.build()
                .get()
                .uri(osmotrDownloadUrl(osmotrHost, osmotrPort, result.getSessionId(), fileType))
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }
}