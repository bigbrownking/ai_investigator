package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.AddFigurantToCaseRequest;
import org.di.digital.dto.request.CreateCaseRequest;
import org.di.digital.dto.request.FileType;
import org.di.digital.dto.response.CaseFileResponse;
import org.di.digital.dto.response.CaseResponse;
import org.di.digital.dto.response.CaseUserResponse;
import org.di.digital.dto.response.FigurantResponse;
import org.di.digital.model.*;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.repository.CaseFileRepository;
import org.di.digital.repository.CaseRepository;
import org.di.digital.repository.FigurantRepository;
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
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseServiceImpl implements CaseService {

    private final WebClient.Builder webClientBuilder;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final TaskQueueService taskQueueService;
    private final LogService logService;
    private final Mapper mapper;
    private final CaseFileRepository caseFileRepository;

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

        int tom = request.getTom();
        String caseNumber = request.getNumber();
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
                    caseFile.addCaseEntity(savedCase);
                    caseFile.setTom(tom);
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
                String.format("Case %s created by user %s",caseNumber, email),
                LogLevel.INFO,
                LogAction.CASE_CREATED,
                caseNumber,
                email
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
    public void updateCaseStatus(Long caseId, boolean status, String email) {
        log.info("Updating status for case: {} to {} by user: {}", caseId, status, email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        String caseNumber = caseEntity.getNumber();
        if (!caseEntity.isOwner(user) && !caseEntity.hasUser(user)) {
            throw new RuntimeException("Access denied: User doesn't have access to this case");
        }

        if (!caseEntity.isOwner(user)) {
            throw new RuntimeException("Access denied: Only case owner can change status");
        }

        caseEntity.setStatus(status);
        caseRepository.save(caseEntity);

        log.info("Case {} status updated to {}", caseId, status);
        logService.log(
                String.format("Case %s status updated to %s by user %s", caseNumber, status, email),
                LogLevel.INFO,
                LogAction.CASE_STATUS_CHANGED,
                caseNumber,
                email
        );
    }

    @Transactional
    public List<CaseFileResponse> addFilesToCase(Long caseId, List<MultipartFile> files,
                                                 FileType type, int tom, String email) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("user not found: " + email));

        if (!caseEntity.isOwner(user) && !caseEntity.hasUser(user)) {
            throw new AccessDeniedException("Access denied to case: " + caseId);
        }


        String caseNumber = caseEntity.getNumber();
        boolean isQualification = type == FileType.QUALIFICATION;

        Set<String> existingFileNames = caseEntity.getFiles().stream()
                .map(CaseFile::getOriginalFileName)
                .collect(Collectors.toSet());

        List<CaseFile> uploadedFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            String originalName = file.getOriginalFilename();

            if (existingFileNames.contains(originalName)) {
                log.warn("File already exists: {} in case: {}", originalName, caseId);
                continue;
            }

            try {
                CaseFile caseFile = minioService.uploadFile(file, caseEntity.getNumber());
                caseFile.addCaseEntity(caseEntity);
                caseFile.setQualification(isQualification);
                caseFile.setTom(tom);
                caseFile.setStatus(CaseFileStatusEnum.QUEUED);
                uploadedFiles.add(caseFile);
            } catch (Exception e) {
                log.error("Failed to upload file: {} to case: {}", originalName, caseId, e);
            }
        }
        List<CaseFile> savedFiles = caseFileRepository.saveAllAndFlush(uploadedFiles);

        savedFiles.forEach(caseFile -> {
            taskQueueService.addTaskToQueue(
                    email,
                    caseEntity.getId(),
                    caseEntity.getNumber(),
                    caseFile.getOriginalFileName(),
                    caseFile.getFileUrl(),
                    caseFile.getId()
            );
        });

        log.info("Added {}/{} files to case: {}", uploadedFiles.size(), files.size(), caseId);

        logService.log(
                String.format("Uploading files by %s user in case %s", email, caseNumber),
                LogLevel.INFO,
                LogAction.FILE_UPLOAD,
                caseNumber,
                email
        );
        return savedFiles.stream()
                .map(mapper::mapToCaseFileResponse)
                .toList();
    }
    @Transactional
    public void deleteFileFromCase(Long caseId, String fileName, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));


        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        String caseNumber = caseEntity.getNumber();
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

            taskQueueService.deleteTask(fileToDelete.getId());

            caseEntity.getFiles().remove(fileToDelete);
            caseRepository.save(caseEntity);
            log.info("Deleted file: {} from case: {}", fileName, caseId);

            logService.log(
                    String.format("Deleted %d file from case %s", fileToDelete.getId(), caseNumber),
                    LogLevel.INFO,
                    LogAction.FILE_DELETE,
                    caseNumber,
                    email
            );

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
    private void deleteAllFilesFromWorkspace(String caseNumber) {
        String url = String.format("http://%s:%s/workspaces/%s",
                pythonHost, pythonPort, caseNumber);

        log.info("🗑️ Deleting all files from workspace: {}", url);

        try {
            webClientBuilder.build()
                    .delete()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("✅ Files deleted from workspace");

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

        String caseNumber = caseEntity.getNumber();
        CaseFile caseFile = caseEntity.getFiles().stream()
                .filter(f -> f.getOriginalFileName().equals(originalFileName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("File not found: " + originalFileName));

        logService.log(
                String.format("Downloaded %d file from case %s", caseFile.getId(), caseNumber),
                LogLevel.INFO,
                LogAction.FILE_DOWNLOAD,
                caseNumber,
                email
        );
        InputStream inputStream = minioService.downloadFile(caseFile.getFileUrl());
        return new InputStreamResource(inputStream);
    }

    @Override
    @Transactional
    public CaseUserResponse addUserToCase(Long caseId, String userEmailToAdd, String currentUserEmail) {
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

        String caseNumber = caseEntity.getNumber();
        caseEntity.addUser(userToAdd);
        Case savedCase = caseRepository.save(caseEntity);

        log.info("User {} added to case {} by {}", userEmailToAdd, caseId, currentUserEmail);

        logService.log(
                String.format("Added %s user to case %s", userEmailToAdd, caseNumber),
                LogLevel.INFO,
                LogAction.USER_ADD,
                caseNumber,
                currentUserEmail
        );
        return mapper.mapToCaseUserResponse(userToAdd, savedCase);
    }

    @Override
    @Transactional
    public FigurantResponse addFigurantToCase(Long caseId, AddFigurantToCaseRequest request, String currentUserEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with id: " + caseId));

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + currentUserEmail));

        if (!caseEntity.isOwner(currentUser) && !caseEntity.hasUser(currentUser)) {
            throw new AccessDeniedException("You don't have permission to add users to this case");
        }

        boolean alreadyExists = caseEntity.getFigurants().stream()
                .anyMatch(f -> f.getFio().equals(request.getFio())
                        && f.getNumber().equals(request.getNumber()));

        if (alreadyExists) {
            throw new IllegalStateException("Figurant already exists in this case");
        }

        Figurant figurant = Figurant.builder()
                .documentType(request.getDocumentType())
                .number(request.getNumber())
                .fio(request.getFio())
                .role(request.getRole())
                .caseEntity(caseEntity)
                .build();

        caseEntity.addFigurant(figurant);
        Case savedCase = caseRepository.save(caseEntity);

        Figurant saved = savedCase.getFigurants().stream()
                .filter(f -> f.getFio().equals(request.getFio()) && f.getNumber().equals(request.getNumber()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Failed to retrieve saved figurant"));

        log.info("Figurant {} added to case {} by {}", request.getFio(), caseId, currentUserEmail);
        return mapper.mapToFigurantResponse(saved);
    }

    @Override
    @Transactional
    public void removeUserFromCase(Long caseId, Long userId, String currentUserEmail) {
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
        String caseNumber = caseEntity.getNumber();

        caseEntity.removeUser(userToRemove);
        caseRepository.save(caseEntity);

        logService.log(
                String.format("Removed %s user to case %s", userToRemove, caseNumber),
                LogLevel.INFO,
                LogAction.USER_DELETE,
                caseNumber,
                currentUserEmail
        );
        log.info("User {} removed from case {} by {}", userToRemove.getEmail(), caseId, currentUserEmail);
    }

    @Override
    @Transactional
    public void removeFigurantFromCase(Long caseId, Long figurantId, String currentUserEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with id: " + caseId));

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + currentUserEmail));

        if (!caseEntity.isOwner(currentUser) && !caseEntity.hasUser(currentUser)) {
            throw new AccessDeniedException("You don't have permission to remove figurants from this case");
        }

        Figurant figurant = caseEntity.getFigurants().stream()
                .filter(f -> f.getId().equals(figurantId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Figurant not found with id: " + figurantId));

        caseEntity.removeFigurant(figurant);
        caseRepository.save(caseEntity);

        log.info("Figurant {} removed from case {} by {}", figurantId, caseId, currentUserEmail);
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
                .map(user -> mapper.mapToCaseUserResponse(user, caseEntity))
                .collect(Collectors.toList());
    }

    @Override
    public List<FigurantResponse> getCaseFigurants(Long caseId, String currentUserEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with id: " + caseId));

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + currentUserEmail));

        if (!caseEntity.isOwner(currentUser) && !caseEntity.hasUser(currentUser)) {
            throw new AccessDeniedException("You don't have permission to view users of this case");
        }
        return caseEntity.getFigurants().stream()
                .map(mapper::mapToFigurantResponse)
                .collect(Collectors.toList());
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

    @Transactional(readOnly = true)
    public Optional<FigurantResponse> findFigurantByNumber(Long caseId, String documentType, String number, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        return caseEntity.getFigurants().stream()
                .filter(f -> number.equals(f.getNumber()) && documentType.equals(f.getDocumentType()))
                .findFirst()
                .map(mapper::mapToFigurantResponse);
    }

    @Override
    public void deleteAllFiles(Long caseId, String currentUserEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with id: " + caseId));

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + currentUserEmail));

        if (!caseEntity.isOwner(currentUser) && !caseEntity.hasUser(currentUser)) {
            throw new AccessDeniedException("You don't have permission to view users of this case");
        }
        String caseNumber = caseEntity.getNumber();
        try {
            deleteAllFilesFromWorkspace(caseEntity.getNumber());

            minioService.deleteAllFilesFromCase(caseEntity.getNumber());

            taskQueueService.deleteTasksByCaseId(caseId);

            caseEntity.removeAllAttachedFiles();
            caseRepository.save(caseEntity);
            log.info("Deleted all files from case: {}", caseId);

            logService.log(
                    String.format("Deleted all files from case %s", caseNumber),
                    LogLevel.INFO,
                    LogAction.FILE_DELETE,
                    caseNumber,
                    currentUserEmail
            );

        } catch (Exception e) {
            log.error("Failed to delete all files from case: {}", caseId, e);
            throw new RuntimeException("Failed to delete all files: ", e);
        }
    }

    @Override
    @Transactional
    public void deleteCaseById(Long caseId, String currentUserEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!caseEntity.isOwner(currentUser)) {
            throw new AccessDeniedException("Only owner can delete case");
        }

        deleteAllFiles(caseId, currentUserEmail);

        caseRepository.delete(caseEntity);

        log.info("Case {} deleted", caseId);
    }
}