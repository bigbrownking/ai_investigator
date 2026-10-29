package org.di.digital.service.impl.osmotr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.OsmotrProcessingMessage;
import org.di.digital.dto.request.osmotr.DistributionRequest;
import org.di.digital.dto.request.osmotr.OsmotrDecisionDto;
import org.di.digital.dto.request.osmotr.OsmotrSearchRequest;
import org.di.digital.dto.request.osmotr.OsmotrSubmitDecisionsRequest;
import org.di.digital.dto.response.osmotr.*;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.IllegalStateMessage;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.MessageConstant;
import org.di.digital.model.enums.osmotr.OsmotrProcessingStatus;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.osmotr.OsmotrResult;
import org.di.digital.model.osmotr.OsmotrResultSegment;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.osmotr.OsmotrResultRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.cases.CaseAccessService;
import org.di.digital.service.core.MinioService;
import org.di.digital.service.osmotr.OsmotrService;
import org.di.digital.service.impl.queue.OsmotrQueueService;
import org.di.digital.util.PdfSplitter;
import org.di.digital.util.mapper.OsmotrMapper;
import org.di.digital.util.requests.UserUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Base64.getDecoder;
import static java.util.Base64.getEncoder;
import static org.di.digital.util.requests.RequestUrlBuilder.*;
import static org.di.digital.util.requests.UserUtil.getCurrentLang;

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
    private final OsmotrMapper mapper;
    private final UserUtil userUtil;
    private final CaseAccessService caseAccessService;

    @Value("${model.host}")
    private String osmotrHost;

    @Value("${osmotr.port}")
    private String osmotrPort;

    @Transactional
    public OsmotrResultDto submitDocument(String caseNumber, String userEmail, MultipartFile file) throws Exception {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), userEmail)));
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));

        userUtil.validateUserAccess(caseEntity, user);
        caseAccessService.require(caseEntity, user, CaseModule.OSMOTR, CaseAction.ADD);

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
            for (String type : List.of("report", "evidence", "return", "resolution")) {
                minioService.deleteFile(buildObjectPath(caseNumber, type));
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
                .language(caseEntity.getAdequateLanguage())
                .build());

        return mapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<OsmotrResultDto> getResultsByCaseNumber(String caseNumber, String email) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));
        userUtil.validateUserAccess(caseEntity, user);
        caseAccessService.require(caseEntity, user, CaseModule.OSMOTR, CaseAction.READ);

        return osmotrResultRepository.findByCaseNumber(caseNumber).stream()
                .map(result -> {
                    OsmotrResultDto dto = mapper.toDto(result);
                    attachReportBase64(result, dto);
                    return dto;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<OsmotrResultDto> getResult(String caseNumber, Long resultId, String email) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));
        userUtil.validateUserAccess(caseEntity, user);
        caseAccessService.require(caseEntity, user, CaseModule.OSMOTR, CaseAction.READ);


        return osmotrResultRepository.findById(resultId)
                .map(result -> {
                    OsmotrResultDto dto = mapper.toDto(result);
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
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));
        userUtil.validateUserAccess(caseEntity, user);
        caseAccessService.require(caseEntity, user, CaseModule.OSMOTR, CaseAction.UPDATE);

        OsmotrResult result = osmotrResultRepository.findById(resultId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.OSMOTR.localized(currentLang(), resultId.toString())));

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
                        .text(s.getInspectionText())
                        .needed(s.getEvidenceNeeded())
                        .build())
                .toList();

        Map<String, Boolean> decisionsMap = saved.getSegments().stream()
                .collect(Collectors.toMap(
                        s -> s.getId().toString(),
                        s -> Boolean.TRUE.equals(s.getEvidenceNeeded())));

        OsmotrResultDto dto = mapper.toDto(saved);

        try {
            OsmotrSubmitDecisionsResponse response = webClientBuilder.build()
                    .post()
                    .uri(osmotrDecisionUrl(osmotrHost, osmotrPort))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(OsmotrSubmitDecisionsRequest.builder()
                            .sessionId(saved.getSessionId())
                            .decisions(decisionsMap)
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
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));
        userUtil.validateUserAccess(caseEntity, user);
        caseAccessService.require(caseEntity, user, CaseModule.OSMOTR, CaseAction.DOWNLOAD);

        OsmotrResult result = osmotrResultRepository.findById(resultId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.OSMOTR.localized(currentLang(), resultId.toString())));

        OsmotrResultSegment segment = result.getSegments().stream()
                .filter(s -> s.getId().equals(segmentId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.SEGMENT.localized(currentLang(), segmentId.toString())));

        try (InputStream is = minioService.downloadFile(segment.getFileUrl())) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang(), resultId.toString()));
        }
    }

    public byte[] mergeSegments(String caseNumber, Long resultId, String type, String email) throws Exception {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));
        userUtil.validateUserAccess(caseEntity, user);
        caseAccessService.require(caseEntity, user, CaseModule.OSMOTR, CaseAction.UPDATE);


        OsmotrResult result = osmotrResultRepository.findById(resultId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.OSMOTR.localized(currentLang(), resultId.toString())));

        List<OsmotrResultSegment> segments = result.getSegments().stream()
                .filter(s -> "EVIDENCE".equals(type)
                        ? Boolean.TRUE.equals(s.getEvidenceNeeded())
                        : Boolean.TRUE.equals(s.getReturnNeeded()))
                .sorted(Comparator.comparing(OsmotrResultSegment::getStartPage))
                .toList();

        if (segments.isEmpty()) {
            throw new NotFoundException(NotFoundMessage.SECTION.localized(currentLang()));
        }

        return pdfSplitter.mergeSegments(segments.stream()
                .map(s -> {
                    try (InputStream is = minioService.downloadFile(s.getFileUrl())) {
                        return is.readAllBytes();
                    } catch (Exception e) {
                        throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang(), resultId.toString()));
                    }
                }).toList());
    }

    public byte[] downloadGeneratedFile(String caseNumber, Long resultId, String fileType, String email) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));
        userUtil.validateUserAccess(caseEntity, user);
        caseAccessService.require(caseEntity, user, CaseModule.OSMOTR, CaseAction.DOWNLOAD);

        OsmotrResult result = osmotrResultRepository.findById(resultId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.OSMOTR.localized(currentLang(), resultId.toString())));

        if (result.getSessionId() == null) {
            throw new IllegalStateException(MessageConstant.OSMOTR_PROCESSING.format(currentLang(), resultId.toString()));
        }

        String objectPath = buildObjectPath(caseNumber, fileType);

        if (!minioService.fileExists(objectPath)) {
            generateAndStoreFile(caseNumber, result, fileType);
        }

        if (minioService.fileExists(objectPath)) {
            try (InputStream is = minioService.downloadFile(objectPath)) {
                log.info("Returning generated file from MinIO: {}", objectPath);
                return is.readAllBytes();
            } catch (Exception e) {
                log.error("Failed to read generated file {}: {}", objectPath, e.getMessage(), e);
                throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang(), resultId.toString()));
            }
        }

        throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang(), resultId.toString()));
    }

    private void generateAndStoreFile(String caseNumber, OsmotrResult result, String fileType) {
        Map<String, Boolean> decisionsMap = result.getSegments().stream()
                .collect(Collectors.toMap(
                        s -> s.getId().toString(),
                        s -> Boolean.TRUE.equals(s.getEvidenceNeeded())));

        try {
            if ("resolution".equals(fileType)) {
                OsmotrResolutionResponse response = webClientBuilder.build()
                        .post()
                        .uri(osmotrResolutionUrl(osmotrHost, osmotrPort))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(OsmotrSubmitDecisionsRequest.builder()
                                .sessionId(result.getSessionId())
                                .decisions(decisionsMap)
                                .build())
                        .retrieve()
                        .bodyToMono(OsmotrResolutionResponse.class)
                        .block();

                if (response != null && response.getResolutionBase64() != null
                        && !response.getResolutionBase64().isBlank()) {
                    overwriteGeneratedFile(caseNumber, "resolution", "resolution.docx", response.getResolutionBase64());
                }

            } else if ("evidence".equals(fileType) || "return".equals(fileType)) {
                OsmotrSubmitDecisionsResponse response = webClientBuilder.build()
                        .post()
                        .uri(osmotrDecisionUrl(osmotrHost, osmotrPort))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(OsmotrSubmitDecisionsRequest.builder()
                                .sessionId(result.getSessionId())
                                .decisions(decisionsMap)
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

            } else {
                log.warn("Unknown fileType '{}', no model request performed", fileType);
            }
        } catch (Exception e) {
            log.error("Failed to generate '{}' file from model for case {}: {}", fileType, caseNumber, e.getMessage(), e);
        }
    }

    public List<OsmotrResultDto> searchSegments(String caseNumber, String query, String email) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));
        userUtil.validateUserAccess(caseEntity, user);
        caseAccessService.require(caseEntity, user, CaseModule.OSMOTR, CaseAction.READ);

        OsmotrResult result = osmotrResultRepository.findFirstByCaseNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.OSMOTR.localized(currentLang(), caseNumber)));

        OsmotrResultDto dto = mapper.toDto(result);

        if (query == null || query.isBlank() || result.getSessionId() == null) {
            return List.of(dto);
        }

        try {
            OsmotrSearchResponse response = webClientBuilder.build()
                    .post()
                    .uri(osmotrSearchUrl(osmotrHost, osmotrPort))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(OsmotrSearchRequest.builder()
                            .sessionId(result.getSessionId())
                            .query(query)
                            .build())
                    .retrieve()
                    .bodyToMono(OsmotrSearchResponse.class)
                    .block();

            if (response != null && response.getResults() != null) {
                Map<Integer, OsmotrResultSegment> dbSegmentMap = result.getSegments().stream()
                        .filter(s -> s.getStartPage() != null)
                        .collect(Collectors.toMap(
                                OsmotrResultSegment::getStartPage,
                                s -> s,
                                (s1, s2) -> s1
                        ));

                List<OsmotrResultSegmentDto> segments = response.getResults().stream()
                        .map(item -> {
                            OsmotrResultSegmentDto.OsmotrResultSegmentDtoBuilder builder = OsmotrResultSegmentDto.builder()
                                    .title(item.getTitle())
                                    .startPage(item.getStartPage())
                                    .endPage(item.getEndPage())
                                    .inspectionText(item.getSnippet());

                            OsmotrResultSegment dbSegment = dbSegmentMap.get(item.getStartPage());
                            if (dbSegment != null) {
                                builder.id(dbSegment.getId())
                                        .evidenceNeeded(dbSegment.getEvidenceNeeded())
                                        .returnNeeded(dbSegment.getReturnNeeded())
                                        .fileUrl(dbSegment.getFileUrl());
                            }

                            return builder.build();
                        })
                        .toList();

                dto.setSegments(segments);
            }
        } catch (Exception e) {
            log.error("Failed to search in AI for caseNumber={}: {}", caseNumber, e.getMessage(), e);
        }

        return List.of(dto);
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

    private UserSettingsLanguage currentLang(){
        return getCurrentLang();
    }
    private String buildObjectPath(String caseNumber, String fileType) {
        return String.format("%s/osmotr/%s/%s.docx.enc", caseNumber, fileType, fileType);
    }
}