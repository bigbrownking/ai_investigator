package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.AddInterrogationRequest;
import org.di.digital.dto.request.CreateCaseRequest;
import org.di.digital.dto.response.CaseInterrogationResponse;
import org.di.digital.dto.response.CaseResponse;
import org.di.digital.model.*;
import org.di.digital.repository.CaseRepository;
import org.di.digital.repository.UserRepository;
import org.di.digital.service.CaseService;
import org.di.digital.util.Mapper;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseServiceImpl implements CaseService {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final TaskQueueService taskQueueService;

    private final Mapper mapper;

    @Transactional
    public CaseResponse createCase(CreateCaseRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        Case newCase = Case.builder()
                .title(request.getTitle())
                .number(request.getNumber())
                .description(request.getDescription())
                .files(new ArrayList<>())
                .user(user)
                .build();

        caseRepository.save(newCase);

        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            for (MultipartFile file : request.getFiles()) {
                if (!file.isEmpty()) {
                    CaseFile caseFile = minioService.uploadFile(file, request.getNumber());
                    caseFile.setCaseEntity(newCase);
                    newCase.getFiles().add(caseFile);
                }
            }
        }

        caseRepository.flush();
        for (CaseFile caseFile : newCase.getFiles()) {
            taskQueueService.addTaskToQueue(
                    email,
                    newCase.getId(),
                    newCase.getNumber(),
                    caseFile.getOriginalFileName(),
                    caseFile.getFileUrl(),
                    caseFile.getId()
            );
            caseFile.setStatus(CaseFileStatusEnum.QUEUED);
        }

        Case savedCase = caseRepository.save(newCase);
        log.info("Case created with id: {} for user: {}", savedCase.getId(), email);

        return mapper.mapToCaseResponse(savedCase);
    }

    @Transactional(readOnly = true)
    public CaseResponse getCaseById(Long id, String email) {
        Case caseEntity = caseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Case not found with id: " + id));

        if (!caseEntity.getUser().getEmail().equals(email)) {
            throw new RuntimeException("Access denied to case: " + id);
        }

        return mapper.mapToCaseResponse(caseEntity);
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> getUserCases(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        return caseRepository.findByUser(user).stream()
                .map(mapper::mapToCaseResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CaseResponse addFilesToCase(Long caseId, List<MultipartFile> files, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        if (!caseEntity.getUser().getEmail().equals(email)) {
            throw new RuntimeException("Access denied to case: " + caseId);
        }
        List<CaseFile> newlyUploadedFiles = new ArrayList<>();
        int addedFilesCount = 0;

        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                try {
                    boolean fileExists = caseEntity.getFiles().stream()
                            .anyMatch(f -> f.getOriginalFileName().equals(file.getOriginalFilename()));

                    if (fileExists) {
                        log.warn("File already exists: {} in case: {}", file.getOriginalFilename(), caseId);
                        continue;
                    }

                    CaseFile caseFile = minioService.uploadFile(file, caseEntity.getNumber());
                    caseFile.setCaseEntity(caseEntity);
                    caseEntity.getFiles().add(caseFile);

                    newlyUploadedFiles.add(caseFile);
                    addedFilesCount++;

                } catch (Exception e) {
                    log.error("Failed to upload file: {} to case: {}", file.getOriginalFilename(), caseId, e);
                    throw new RuntimeException("Failed to upload file: " + file.getOriginalFilename(), e);
                }
            }
        }
        caseRepository.flush();
        for (CaseFile caseFile : newlyUploadedFiles) {
            taskQueueService.addTaskToQueue(
                    email,
                    caseEntity.getId(),
                    caseEntity.getNumber(),
                    caseFile.getOriginalFileName(),
                    caseFile.getFileUrl(),
                    caseFile.getId()
            );
            caseFile.setStatus(CaseFileStatusEnum.QUEUED);
        }

        Case savedCase = caseRepository.save(caseEntity);
        log.info("Added {} files to case: {}", addedFilesCount, caseId);

        return mapper.mapToCaseResponse(savedCase);
    }

    @Transactional
    public CaseResponse deleteFileFromCase(Long caseId, String fileName, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        if (!caseEntity.getUser().getEmail().equals(email)) {
            throw new RuntimeException("Access denied to case: " + caseId);
        }

        CaseFile fileToDelete = caseEntity.getFiles().stream()
                .filter(f -> f.getOriginalFileName().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("File not found: " + fileName));

        try {
            minioService.deleteFile(fileToDelete.getFileUrl());

            caseEntity.getFiles().remove(fileToDelete);

            Case savedCase = caseRepository.save(caseEntity);
            log.info("Deleted file: {} from case: {}", fileName, caseId);

            return mapper.mapToCaseResponse(savedCase);

        } catch (Exception e) {
            log.error("Failed to delete file: {} from case: {}", fileName, caseId, e);
            throw new RuntimeException("Failed to delete file: " + fileName, e);
        }
    }

    @Transactional(readOnly = true)
    public InputStreamResource downloadFile(Long caseId, String originalFileName, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        if (!caseEntity.getUser().getEmail().equals(email)) {
            throw new RuntimeException("Access denied");
        }

        CaseFile caseFile = caseEntity.getFiles().stream()
                .filter(f -> f.getOriginalFileName().equals(originalFileName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("File not found: " + originalFileName));

        InputStream inputStream = minioService.downloadFile(caseFile.getFileUrl());
        return new InputStreamResource(inputStream);
    }

    @Transactional(readOnly = true)
    public List<CaseInterrogationResponse> searchInterrogations(Long caseId, String role, String fio, LocalDate date, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        if (!caseEntity.getUser().getEmail().equals(email)) {
            throw new RuntimeException("Access denied to case: " + caseId);
        }

        return caseEntity.getInterrogations().stream()
                .filter(i -> role.equals("Все") || i.getRole().equalsIgnoreCase(role))
                .filter(i -> fio == null || i.getFio().toLowerCase().contains(fio.toLowerCase()))
                .filter(i -> date == null || i.getDate().equals(date))
                .map(mapper::mapToInterrogationResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CaseResponse addInterrogation(Long caseId, AddInterrogationRequest request, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        if (!caseEntity.getUser().getEmail().equals(email)) {
            throw new RuntimeException("Access denied to case: " + caseId);
        }

        CaseInterrogation interrogation = CaseInterrogation.builder()
                .iin(request.getIin())
                .fio(request.getFio())
                .role(request.getRole())
                .date(LocalDate.now())
                .caseEntity(caseEntity)
                .status(CaseInterrogationStatusEnum.IN_PROGRESS)
                .build();

        caseEntity.getInterrogations().add(interrogation);
        caseRepository.save(caseEntity);

        log.info("Interrogation added to case: {}", caseId);
        return mapper.mapToCaseResponse(caseEntity);
    }
}