package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.CreateCaseRequest;
import org.di.digital.dto.request.FileType;
import org.di.digital.dto.response.CaseResponse;
import org.di.digital.dto.response.CaseUserResponse;
import org.di.digital.model.*;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.repository.CaseFileRepository;
import org.di.digital.repository.CaseRepository;
import org.di.digital.repository.UserRepository;
import org.di.digital.service.CaseService;
import org.di.digital.service.LogService;
import org.di.digital.service.impl.queue.TaskQueueService;
import org.di.digital.util.Mapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseServiceImpl implements CaseService {

    private final WebClient.Builder webClientBuilder;
    private final CaseRepository caseRepository;
    private final CaseFileRepository caseFileRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final TaskQueueService taskQueueService;
    private final LogService logService;
    private final Mapper mapper;

    @Value("${qualification.model.host}")
    private String pythonHost;

    @Value("${index.control.port}")
    private String pythonPort;


    @Transactional
    public CaseResponse createCase(CreateCaseRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        if(caseRepository.existsByNumber(request.getNumber())){
            throw new RuntimeException("Case already exists, please inform the case creator.");
        }

        Case newCase = Case.builder()
                .title(request.getTitle())
                .number(request.getNumber())
                .description(request.getDescription())
                .files(new ArrayList<>())
                .owner(user)
                .users(new HashSet<>())
                .build();

        newCase.addUser(user);

        Case savedCase = caseRepository.save(newCase);

        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            for (MultipartFile file : request.getFiles()) {
                if (!file.isEmpty()) {
                    CaseFile caseFile = minioService.uploadFile(file, request.getNumber());
                    caseFile.setCaseEntity(savedCase);
                    savedCase.getFiles().add(caseFile);
                }
            }
        }

        caseRepository.flush();

        for (CaseFile caseFile : savedCase.getFiles()) {
            taskQueueService.addTaskToQueue(
                    email,
                    savedCase.getId(),
                    savedCase.getNumber(),
                    caseFile.getOriginalFileName(),
                    caseFile.getFileUrl(),
                    caseFile.getId()
            );
            caseFile.setStatus(CaseFileStatusEnum.QUEUED);
        }

        log.info("Case created with id: {} for user: {}", savedCase.getId(), email);

        logService.log(
                String.format("Case %s created by user %s", savedCase.getNumber(), email),
                LogLevel.INFO,
                LogAction.CASE_CREATED,
                savedCase
        );

        return mapper.mapToCaseResponse(savedCase);
    }

    @Transactional(readOnly = true)
    public CaseResponse getCaseById(Long id, String email) {
        Case caseEntity = caseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Case not found with id: " + id));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        if (!caseEntity.isOwner(user) && !caseEntity.hasUser(user)) {
            throw new AccessDeniedException("Access denied to case: " + id);
        }

        return mapper.mapToCaseResponse(caseEntity);
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> getUserCases(String email, String sort) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        Comparator<Case> comparator = "asc".equalsIgnoreCase(sort)
                ? Comparator.comparing(Case::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder()))
                : Comparator.comparing(Case::getCreatedDate, Comparator.nullsLast(Comparator.reverseOrder()));

        return user.getCases().stream()
                .sorted(comparator)
                .map(mapper::mapToCaseResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CaseResponse updateCaseStatus(Long caseId, boolean status, String email) {
        log.info("Updating status for case: {} to {} by user: {}", caseId, status, email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        if (!caseEntity.isOwner(user) && !caseEntity.hasUser(user)) {
            throw new RuntimeException("Access denied: User doesn't have access to this case");
        }

        if (!caseEntity.isOwner(user)) {
            throw new RuntimeException("Access denied: Only case owner can change status");
        }

        caseEntity.setStatus(status);
        Case savedCase = caseRepository.save(caseEntity);

        log.info("Case {} status updated to {}", caseId, status);
        logService.log(
                String.format("Case %s status updated to %s by user %s", caseEntity.getNumber(), status, email),
                LogLevel.INFO,
                LogAction.CASE_STATUS_CHANGED,
                caseEntity
        );
        return mapper.mapToCaseResponse(savedCase);
    }

    @Transactional
    public CaseResponse addFilesToCase(Long caseId, List<MultipartFile> files, FileType type, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        if (!caseEntity.isOwner(user) && !caseEntity.hasUser(user)) {
            throw new AccessDeniedException("Access denied to case: " + caseId);
        }

        List<CaseFile> newlyUploadedFiles = new ArrayList<>();
        int addedFilesCount = 0;
        boolean isQualification = type == FileType.QUALIFICATION;

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
                    caseFile.setQualification(isQualification);
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

        log.info("Added {} files to case: {}", addedFilesCount, caseId);
        logService.log(
                String.format("Added %d file(s) to case %s", addedFilesCount, caseEntity.getNumber()),
                LogLevel.INFO,
                LogAction.FILE_UPLOAD,
                caseEntity
        );
        return mapper.mapToCaseResponse(caseEntity);
    }

    @Transactional
        public CaseResponse deleteFileFromCase(Long caseId, String fileName, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));


        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        if (!caseEntity.isOwner(user) && !caseEntity.hasUser(user)) {
            throw new AccessDeniedException("Access denied to case: " + caseId);
        }

        CaseFile fileToDelete = caseEntity.getFiles().stream()
                .filter(f -> f.getOriginalFileName().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("File not found: " + fileName));

        try {
            deleteFileFromWorkspace(caseEntity.getNumber(), fileName);

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
    private void deleteFileFromWorkspace(String caseNumber, String fileName) {
        String url = String.format("http://%s:%s/workspaces/delete_by_case_id/%s/%s",
                pythonHost, pythonPort, caseNumber, fileName);

        log.info("🗑️ Deleting file from workspace: {}", url);

        try {
            webClientBuilder.build()
                    .post()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("✅ File deleted from workspace: {}", fileName);

        } catch (Exception e) {
            log.warn("⚠️ Failed to delete file from workspace (continuing anyway): {}", e.getMessage());
        }
    }
    @Transactional(readOnly = true)
    public InputStreamResource downloadFile(Long caseId, String originalFileName, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        if (!caseEntity.isOwner(user) && !caseEntity.hasUser(user)) {
            throw new AccessDeniedException("Access denied");
        }

        CaseFile caseFile = caseEntity.getFiles().stream()
                .filter(f -> f.getOriginalFileName().equals(originalFileName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("File not found: " + originalFileName));

        InputStream inputStream = minioService.downloadFile(caseFile.getFileUrl());
        return new InputStreamResource(inputStream);
    }

    @Override
    @Transactional
    public CaseResponse addUserToCase(Long caseId, String userEmailToAdd, String currentUserEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with id: " + caseId));

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + currentUserEmail));

        if (!caseEntity.isOwner(currentUser) && !caseEntity.hasUser(currentUser)) {
            throw new AccessDeniedException("You don't have permission to add users to this case");
        }

        User userToAdd = userRepository.findByEmail(userEmailToAdd)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmailToAdd));

        if (caseEntity.hasUser(userToAdd)) {
            throw new IllegalStateException("User is already added to this case");
        }

        caseEntity.addUser(userToAdd);
        Case savedCase = caseRepository.save(caseEntity);

        log.info("User {} added to case {} by {}", userEmailToAdd, caseId, currentUserEmail);

        return mapper.mapToCaseResponse(savedCase);
    }

    @Override
    @Transactional
    public CaseResponse removeUserFromCase(Long caseId, Long userId, String currentUserEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with id: " + caseId));

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + currentUserEmail));

        if (!caseEntity.isOwner(currentUser)) {
            throw new AccessDeniedException("Only the case owner can remove users");
        }

        User userToRemove = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        if (caseEntity.isOwner(userToRemove)) {
            throw new IllegalStateException("Cannot remove the case owner");
        }

        if (!caseEntity.hasUser(userToRemove)) {
            throw new IllegalStateException("User is not a member of this case");
        }

        caseEntity.removeUser(userToRemove);
        Case savedCase = caseRepository.save(caseEntity);

        log.info("User {} removed from case {} by {}", userToRemove.getEmail(), caseId, currentUserEmail);

        return mapper.mapToCaseResponse(savedCase);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaseUserResponse> getCaseUsers(Long caseId, String currentUserEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with id: " + caseId));

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + currentUserEmail));

        if (!caseEntity.isOwner(currentUser) && !caseEntity.hasUser(currentUser)) {
            throw new AccessDeniedException("You don't have permission to view users of this case");
        }

        return caseEntity.getUsers().stream()
                .map(user -> CaseUserResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .name(user.getName())
                        .surname(user.getSurname())
                        .fathername(user.getFathername())
                        .isOwner(caseEntity.isOwner(user))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateCaseActivity(Long caseId, String activityType) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        caseEntity.updateActivity(activityType);
        caseRepository.save(caseEntity);

        log.debug("Updated activity for case {}: {}", caseId, activityType);
    }

    @Transactional
    public void updateCaseActivity(String caseNumber, String activityType) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseNumber));

        caseEntity.updateActivity(activityType);
        caseRepository.save(caseEntity);

        log.debug("Updated activity for case {}: {}", caseNumber, activityType);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CaseResponse> getRecentCases(String userEmail, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Case> cases = caseRepository.findRecentCasesWithActivity(userEmail, pageable);

        return cases.map(mapper::mapToCaseResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CaseResponse> getCasesByActivityType(String userEmail, String activityType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Case> cases = caseRepository.findCasesByActivityType(userEmail, activityType, pageable);

        return cases.map(mapper::mapToCaseResponse);
    }
}