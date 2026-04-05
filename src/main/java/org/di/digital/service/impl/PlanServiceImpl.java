package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.CaseFileResponse;
import org.di.digital.model.Case;
import org.di.digital.model.CaseFile;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.repository.CaseFileRepository;
import org.di.digital.repository.CaseRepository;
import org.di.digital.service.MinioService;
import org.di.digital.service.PlanService;
import org.di.digital.service.impl.queue.TaskQueueService;
import org.di.digital.util.Mapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.di.digital.util.UrlBuilder.planGeneratorUrl;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {
    private final CaseRepository caseRepository;
    private final CaseFileRepository caseFileRepository;
    private final MinioService minioService;
    private final WebClient.Builder webClientBuilder;
    private final TaskQueueService taskQueueService;

    private final Mapper mapper;

    @Value("${plan.generator.host}")
    private String planHost;

    @Value("${plan.generator.port}")
    private String planPort;

    @Override
    public CaseFileResponse generatePlan(Long caseId, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        List<CaseFile> planSourceFiles = caseEntity.getFiles().stream()
                .filter(CaseFile::isPlanComponent)
                .toList();

        if (planSourceFiles.isEmpty()) {
            throw new RuntimeException("No source files attached for plan generation in case: " + caseId);
        }

        String url = planGeneratorUrl(planHost, planPort);

        try {
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

            for (CaseFile planFile : planSourceFiles) {
                byte[] bytes = minioService.downloadFile(planFile.getFileUrl()).readAllBytes();
                String filename = planFile.getOriginalFileName();

                bodyBuilder.part("documents", new ByteArrayResource(bytes) {
                    @Override
                    public String getFilename() { return filename; }
                }).contentType(MediaType.APPLICATION_PDF);
            }

            bodyBuilder.part("model", "qwen3-next-80b-instruct");
            bodyBuilder.part("api_url", "http://192.168.97.5:8805/v1/chat/completions");
            bodyBuilder.part("methodology_dir", "data/methodology");

            byte[] responseBytes = webClientBuilder
                    .build()
                    .post()
                    .uri(url)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(bodyBuilder.build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                            response.bodyToMono(String.class)
                                    .doOnNext(body -> log.error("4xx error from plan generator: {}", body))
                                    .map(body -> new RuntimeException("Plan generator error: " + body))
                    )
                    .bodyToMono(byte[].class)
                    .block();

            caseEntity.getFiles().stream()
                    .filter(CaseFile::isPlan)
                    .findFirst()
                    .ifPresent(old -> {
                        minioService.deleteFile(old.getFileUrl());
                        caseEntity.getFiles().remove(old);
                    });

            caseRepository.save(caseEntity);
            String planFilename = "plan_" + caseEntity.getNumber() + ".docx";
            String objectPath = minioService.uploadPlanBytes(
                    responseBytes,
                    caseEntity.getNumber(),
                    planFilename
            );

            CaseFile planFile = CaseFile.builder()
                    .originalFileName(planFilename)
                    .storedFileName(planFilename)
                    .fileUrl(objectPath)
                    .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .fileSize((long) responseBytes.length)
                    .uploadedAt(LocalDateTime.now())
                    .status(CaseFileStatusEnum.UPLOADED)
                    .isPlan(true)
                    .build();

            planFile.addCaseEntity(caseEntity);
            CaseFile saved = caseFileRepository.save(planFile);

            log.info("Investigation plan generated and saved for case: {}", caseId);
            return mapper.mapToCaseFileResponse(saved);

        } catch (Exception e) {
            log.error("Failed to generate plan for case: {}", caseId, e);
            throw new RuntimeException("Failed to generate plan", e);
        }
    }

    @Override
    public CaseFileResponse getPlan(Long caseId) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        CaseFile planFile = caseEntity.getFiles().stream()
                .filter(CaseFile::isPlan)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Plan not found for case: " + caseId));

        log.info("Returning plan for case: {}", caseId);
        return mapper.mapToCaseFileResponse(planFile);
    }

    @Override
    public boolean hasPlan(Long caseId) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        return caseEntity.getFiles().stream()
                .anyMatch(CaseFile::isPlan);
    }

    @Override
    public List<CaseFileResponse> addPlanFiles(Long caseId, List<MultipartFile> files, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        List<CaseFileResponse> savedFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            String objectPath = minioService.uploadPlan(file, caseEntity.getNumber());

            CaseFile caseFile = CaseFile.builder()
                    .originalFileName(file.getOriginalFilename())
                    .storedFileName(file.getOriginalFilename())
                    .fileUrl(objectPath)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .uploadedAt(LocalDateTime.now())
                    .status(CaseFileStatusEnum.QUEUED)
                    .isPlanComponent(true)
                    .build();

            caseFile.addCaseEntity(caseEntity);
            CaseFile saved = caseFileRepository.save(caseFile);

            taskQueueService.addTaskToQueue(
                    email,
                    caseEntity.getId(),
                    caseEntity.getNumber(),
                    saved.getOriginalFileName(),
                    saved.getFileUrl(),
                    saved.getId()
            );

            savedFiles.add(mapper.mapToCaseFileResponse(saved));
        }

        log.info("Added {} plan source files for case: {}", savedFiles.size(), caseId);
        return savedFiles;
    }

    @Override
    public List<CaseFileResponse> getPlanFiles(Long caseId) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        return caseEntity.getFiles().stream()
                .filter(CaseFile::isPlanComponent)
                .map(mapper::mapToCaseFileResponse)
                .toList();
    }
}