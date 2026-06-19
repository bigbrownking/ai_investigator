package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.cases.ReorderCaseFilesRequest;
import org.di.digital.dto.response.cases.*;
import org.di.digital.dto.response.interrogation.FigurantResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.enums.MessageConstant;
import org.di.digital.dto.request.interrogation.AddFigurantToCaseRequest;
import org.di.digital.dto.request.cases.CreateCaseRequest;
import org.di.digital.dto.request.cases.EditCaseRequest;
import org.di.digital.model.enums.FileType;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.model.interrogation.CaseFigurant;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.CaseService;
import org.di.digital.service.LogService;
import org.di.digital.service.MinioService;
import org.di.digital.service.impl.queue.TaskQueueService;
import org.di.digital.util.Mapper;
import org.di.digital.util.PageCounter;
import org.di.digital.util.requests.RequestUrlBuilder;
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
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static org.di.digital.util.requests.UserUtil.validateUserAccess;

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
    private final PageCounter pageCounter;
    private final CaseFileRepository caseFileRepository;

    private static final int MAX_PAGES_PER_TOM = 180;


    @Value("${model.host}")
    private String pythonHost;

    @Value("${index.port}")
    private String pythonPort;

    @Override
    @Transactional(readOnly = true)
    public Case getCaseEntityById(Long caseId, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));

        validateUserAccess(caseEntity, user);

        return caseEntity;
    }
    private void assignToms(List<CaseFile> newFiles, List<CaseFile> existingFiles) {
        Map<Integer, Integer> tomPageCounts = new HashMap<>();
        for (CaseFile f : existingFiles) {
            int t = f.getTom() == null ? 1 : f.getTom();
            int pages = f.getPages() == null ? 0 : f.getPages();
            tomPageCounts.merge(t, pages, Integer::sum);
        }

        int currentTom = tomPageCounts.isEmpty() ? 1
                : Collections.max(tomPageCounts.keySet());
        int currentPages = tomPageCounts.getOrDefault(currentTom, 0);

        for (CaseFile file : newFiles) {
            int filePages = file.getPages() == null ? 0 : file.getPages();

            if (currentPages > 0 && currentPages + filePages > MAX_PAGES_PER_TOM) {
                currentTom++;
                currentPages = 0;
            }

            file.setTom(currentTom);
            currentPages += filePages;
        }
    }
    @Transactional
    public CaseResponse createCase(CreateCaseRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));

        if (caseRepository.existsByNumber(request.getNumber())) {
            logService.log(
                    String.format("Case already exists: %s", request.getNumber()),
                    LogLevel.ERROR,
                    LogAction.CASE_CREATED,
                    request.getNumber(),
                    user.getEmail()
            );
            throw new IllegalStateException("Дело уже создано, пожалуйста проинформируйте создателя дела.");
        }

        String caseNumber = request.getNumber();
        Case newCase = Case.builder()
                .title(request.getTitle())
                .number(request.getNumber())
                .files(new ArrayList<>())
                .owner(user)
                .users(new HashSet<>())
                .build();

        newCase.addUser(user);
        Case savedCase = caseRepository.save(newCase);

        List<CaseFile> uploadedFiles = new ArrayList<>();

        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            for (MultipartFile file : request.getFiles()) {
                if (!file.isEmpty()) {
                    CaseFile caseFile = minioService.uploadFile(file, request.getNumber());
                    caseFile.addCaseEntity(savedCase);
                    caseFile.setStatus(CaseFileStatusEnum.QUEUED);
                    uploadedFiles.add(caseFile);
                }
            }
        }

        uploadedFiles.forEach(caseFile -> {
            try {
                Integer pages = pageCounter.countPagesByUrl(caseFile.getFileUrl(), caseFile.getContentType());
                if (pages != null) {
                    caseFile.setPages(pages);
                }
            } catch (Exception e) {
                log.warn("Could not count pages for file {}: {}", caseFile.getOriginalFileName(), e.getMessage());
            }
        });
        assignToms(uploadedFiles, Collections.emptyList());

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
        }

        log.info("Case created with id: {} for user: {}", savedCase.getId(), email);

        logService.log(
                String.format("Case %s created by user %s", caseNumber, email),
                LogLevel.INFO,
                LogAction.CASE_CREATED,
                caseNumber,
                email
        );

        return mapper.mapToCaseResponse(savedCase);
    }

    @Override
    @Transactional
    public CaseResponse editCase(Long caseId, EditCaseRequest request, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));

        validateUserAccess(caseEntity, user);

        if (!caseEntity.isOwner(user)) {
            throw new AccessDeniedException("Только создатель дела может редактировать его");
        }

        String oldNumber = caseEntity.getNumber();

        if (request.getNumber() != null && !request.getNumber().equals(oldNumber)) {
            if (caseRepository.existsByNumber(request.getNumber())) {
                logService.log(
                        String.format("Case already exists: %s", request.getNumber()),
                        LogLevel.ERROR,
                        LogAction.CASE_UPDATED,
                        request.getNumber(),
                        user.getEmail()
                );
                throw new IllegalStateException(MessageConstant.WORKSPACE_ALREADY_EXISTS.format(request.getNumber()));
            }

            renameWorkspace(oldNumber, request.getNumber());
            caseEntity.setNumber(request.getNumber());
        }

        if (request.getTitle() != null) {
            caseEntity.setTitle(request.getTitle());
        }

        Case savedCase = caseRepository.save(caseEntity);

        log.info("Case {} edited by user: {}", caseId, email);

        logService.log(
                String.format("Case %s edited by user %s", savedCase.getNumber(), email),
                LogLevel.INFO,
                LogAction.CASE_UPDATED,
                savedCase.getNumber(),
                email
        );

        return mapper.mapToCaseResponse(savedCase);
    }
    private void renameWorkspace(String oldNumber, String newNumber) {
        String url = RequestUrlBuilder.renameWorkspaceUrl(pythonHost, pythonPort, oldNumber);
        Map<String, String> body = Map.of("new_name", newNumber);

        log.info("Renaming workspace from {} to {}", oldNumber, newNumber);

        try {
            webClientBuilder.build()
                    .patch()
                    .uri(url)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(
                            status -> status.value() == 409,
                            response -> Mono.error(new IllegalStateException(
                                    MessageConstant.WORKSPACE_ALREADY_EXISTS.format(newNumber)
                            ))
                    )
                    .bodyToMono(String.class)
                    .block();

            log.info("Workspace renamed successfully: {} -> {}", oldNumber, newNumber);

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn(MessageConstant.WORKSPACE_RENAME_FAILED.format(oldNumber, newNumber) + ": " + e.getMessage());
        }
    }
    @Transactional(readOnly = true)
    public CaseResponse getCaseById(Long id, String email) {
        Case caseEntity = caseRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Case not found with id: " + id));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));

        validateUserAccess(caseEntity, user);

        return mapper.mapToCaseResponse(caseEntity);
    }
    @Transactional
    public GroupedCaseFileResponse recalculateToms(Long caseId, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));

        validateUserAccess(caseEntity, user);

        List<CaseFile> orderedFiles = caseEntity.getFiles().stream()
                .sorted(Comparator.comparing(CaseFile::getOrderIndex,
                        Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());

        for (int i = 0; i < orderedFiles.size(); i++) {
            if (orderedFiles.get(i).getOrderIndex() == null) {
                orderedFiles.get(i).setOrderIndex(i);
            }
        }

        assignToms(orderedFiles, Collections.emptyList());

        caseFileRepository.saveAll(orderedFiles);

        logService.log(
                String.format("Toms recalculated in case %s by user %s", caseEntity.getNumber(), email),
                LogLevel.INFO, LogAction.FILE_REORDER,
                caseEntity.getNumber(), email
        );

        return getGroupedCaseFilesById(caseId, email);
    }
    @Override
    @Transactional
    public GroupedCaseFileResponse getGroupedCaseFilesById(Long id, String email) {

        Case caseEntity = caseRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Case not found"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        validateUserAccess(caseEntity, user);

        Map<Integer, List<CaseFile>> grouped =
                caseEntity.getFiles().stream()
                        .sorted(Comparator
                                .comparing(CaseFile::getTom, Comparator.nullsLast(Integer::compareTo))
                                .thenComparing(CaseFile::getOrderIndex,
                                        Comparator.nullsLast(Integer::compareTo)))
                        .collect(Collectors.groupingBy(
                                file -> file.getTom() == null ? 0 : file.getTom(),
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        List<TomGroupResponse> toms = grouped.entrySet().stream()
                .map(entry -> {
                    Integer tom = entry.getKey();
                    List<CaseFile> tomFiles = entry.getValue();

                    int[] pageCounter = {1};

                    List<CaseFileResponse> files = tomFiles.stream()
                            .map(f -> {
                                CaseFileResponse dto = mapper.mapToCaseFileResponse(f);
                                int pages = f.getPages() == null ? 0 : f.getPages();

                                if (pages > 0) {
                                    f.setStartPage(pageCounter[0]);
                                    f.setEndPage(pageCounter[0] + pages - 1);
                                    dto.setStartPage(pageCounter[0]);
                                    dto.setEndPage(pageCounter[0] + pages - 1);
                                    pageCounter[0] += pages;
                                } else {
                                    f.setStartPage(null);
                                    f.setEndPage(null);
                                    dto.setStartPage(null);
                                    dto.setEndPage(null);
                                }

                                return dto;
                            })
                            .toList();

                    caseFileRepository.saveAll(tomFiles);

                    int totalPages = pageCounter[0] - 1;

                    return TomGroupResponse.builder()
                            .tom(tom)
                            .files(files)
                            .totalFiles(files.size())
                            .totalPages(totalPages)
                            .build();
                })
                .toList();

        int totalPages = toms.stream()
                .mapToInt(TomGroupResponse::getTotalPages)
                .sum();

        int totalFiles = toms.stream()
                .mapToInt(TomGroupResponse::getTotalFiles)
                .sum();

        return GroupedCaseFileResponse.builder()
                .toms(toms)
                .totalPages(totalPages)
                .totalFiles(totalFiles)
                .build();
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> getUserCases(String email, String sort) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));

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
                .orElseThrow(() -> new IllegalStateException("User not found"));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Case not found"));

        String caseNumber = caseEntity.getNumber();
        validateUserAccess(caseEntity, user);

        if (!caseEntity.isOwner(user)) {
            throw new AccessDeniedException("Только создатель дела может изменять его статус");
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
                                                 FileType type, String email) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("user not found: " + email));

        validateUserAccess(caseEntity, user);

        if (Boolean.TRUE.equals(caseEntity.getIsFinalIndictmentDone())) {
            logService.log(
                    String.format("Cannot upload files by %s user in case %s: [%s]", email, caseEntity.getNumber(),
                            files.stream().map(MultipartFile::getOriginalFilename).collect(Collectors.joining(", "))),
                    LogLevel.ERROR,
                    LogAction.FILE_UPLOAD,
                    caseEntity.getNumber(),
                    email
            );
            throw new IllegalStateException(MessageConstant.CANNOT_UPLOAD_FILE.format(caseEntity.getNumber()));
        }

        String caseNumber = caseEntity.getNumber();
        boolean isQualification = type == FileType.QUALIFICATION;

        Set<String> existingFileNames = caseEntity.getFiles().stream()
                .map(CaseFile::getOriginalFileName)
                .collect(Collectors.toSet());

        List<CaseFile> existingFiles = new ArrayList<>(caseEntity.getFiles());
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
                caseFile.setStatus(CaseFileStatusEnum.QUEUED);
                uploadedFiles.add(caseFile);
            } catch (Exception e) {
                log.error("Failed to upload file: {} to case: {}", originalName, caseId, e);
            }
        }

        uploadedFiles.forEach(caseFile -> {
            try {
                Integer pages = pageCounter.countPagesByUrl(caseFile.getFileUrl(), caseFile.getContentType());
                if (pages != null) {
                    caseFile.setPages(pages);
                }
            } catch (Exception e) {
                log.warn("Could not count pages for file {}: {}", caseFile.getOriginalFileName(), e.getMessage());
            }
        });
        assignToms(uploadedFiles, existingFiles);

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

        String fileNames = savedFiles.stream()
                .map(CaseFile::getOriginalFileName)
                .collect(Collectors.joining(", "));

        logService.log(
                String.format("Uploading files by %s user in case %s: [%s]", email, caseNumber, fileNames),
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
    public GroupedCaseFileResponse reorderCaseFiles(Long caseId, ReorderCaseFilesRequest request,
                                                    String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        validateUserAccess(caseEntity, user);

        List<Long> fileIds = request.getFileIds();

        Map<Long, CaseFile> fileMap = caseEntity.getFiles().stream()
                .collect(Collectors.toMap(CaseFile::getId, f -> f));

        if (fileIds.size() != fileMap.size() || !fileMap.keySet().containsAll(fileIds)) {
            throw new IllegalArgumentException("File IDs do not match case files");
        }

        List<CaseFile> orderedFiles = fileIds.stream()
                .map(fileMap::get)
                .collect(Collectors.toList());

        for (int i = 0; i < orderedFiles.size(); i++) {
            orderedFiles.get(i).setOrderIndex(i);
        }

        assignToms(orderedFiles, Collections.emptyList());

        caseFileRepository.saveAll(orderedFiles);

        logService.log(
                String.format("Files reordered in case %s by user %s", caseEntity.getNumber(), email),
                LogLevel.INFO, LogAction.FILE_REORDER,
                caseEntity.getNumber(), email
        );

        return getGroupedCaseFilesById(caseId, email);
    }


    @Transactional
    public void deleteFileFromCase(Long caseId, String fileName, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        String caseNumber = caseEntity.getNumber();
        validateUserAccess(caseEntity, user);

        CaseFile fileToDelete = caseEntity.getFiles().stream()
                .filter(f -> f.getOriginalFileName().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("File not found: " + fileName));

        if(CaseFileStatusEnum.PROCESSING.equals(fileToDelete.getStatus())){

            String message = MessageConstant.CANNOT_DELETE_FILE.format(caseNumber);
            log.warn(message);
            logService.log(
                    String.format("Cannot delete processing file in case %s", caseEntity.getNumber()),
                    LogLevel.ERROR,
                    LogAction.FILE_DELETE,
                    caseEntity.getNumber(),
                    user.getEmail()
            );
            throw new IllegalStateException(message);
        }
        try {
            if(CaseFileStatusEnum.COMPLETED.equals(fileToDelete.getStatus())){
                deleteFileFromWorkspace(caseEntity.getNumber(), fileName);
            }

            minioService.deleteFile(fileToDelete.getFileUrl());

            taskQueueService.deleteTask(fileToDelete.getId());

            caseEntity.getFiles().remove(fileToDelete);
            caseRepository.save(caseEntity);
            log.info("Deleted file: {} from case: {}", fileName, caseId);

            logService.log(
                    String.format("Deleted %s file from case %s", fileToDelete.getOriginalFileName(), caseNumber),
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

        validateUserAccess(caseEntity, user);

        String caseNumber = caseEntity.getNumber();
        CaseFile caseFile = caseEntity.getFiles().stream()
                .filter(f -> f.getOriginalFileName().equals(originalFileName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("File not found: " + originalFileName));

        logService.log(
                String.format("Downloaded %s file from case %s", caseFile.getOriginalFileName(), caseNumber),
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

        validateUserAccess(caseEntity, currentUser);

        User userToAdd = userRepository.findByEmail(userEmailToAdd)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmailToAdd));

        if (caseEntity.hasUser(userToAdd)) {
            logService.log(
                    String.format("User '%s' already added to case №%s by user %s",
                            userEmailToAdd, caseEntity.getNumber(), currentUserEmail),
                    LogLevel.ERROR,
                    LogAction.USER_ADD,
                    caseEntity.getNumber(),
                    currentUserEmail
            );
            throw new IllegalStateException("User is already added to this case");
        }

        String caseNumber = caseEntity.getNumber();
        caseEntity.addUser(userToAdd);
        Case savedCase = caseRepository.save(caseEntity);

        log.info("User {} added to case {} by {}", userEmailToAdd, caseId, currentUserEmail);

        logService.log(
                String.format("User '%s' added to case №%s by user %s",
                        userEmailToAdd, caseNumber, currentUserEmail),
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

        validateUserAccess(caseEntity, currentUser);

        boolean alreadyExists = caseEntity.getFigurants().stream()
                .anyMatch(f -> f.getFio().equals(request.getFio())
                        && f.getNumber().equals(request.getNumber()));

        if (alreadyExists) {
            logService.log(
                    String.format("Figurant '%s' already added to case №%s by user %s",
                            request.getFio(), caseEntity.getNumber(), currentUserEmail),
                    LogLevel.ERROR,
                    LogAction.FIGURANT_ADDED,
                    caseEntity.getNumber(),
                    currentUserEmail
            );
            throw new IllegalStateException("Figurant already exists in this case");
        }

        CaseFigurant figurant = CaseFigurant.builder()
                .documentType(request.getDocumentType())
                .number(request.getNumber())
                .fio(request.getFio())
                .role(request.getRole())
                .caseEntity(caseEntity)
                .build();

        caseEntity.addFigurant(figurant);
        Case savedCase = caseRepository.save(caseEntity);

        CaseFigurant saved = savedCase.getFigurants().stream()
                .filter(f -> f.getFio().equals(request.getFio()) && f.getNumber().equals(request.getNumber()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Failed to retrieve saved figurant"));

        log.info("Figurant {} added to case {} by {}", request.getFio(), caseId, currentUserEmail);
        logService.log(
                String.format("Figurant '%s' added to case №%s by user %s",
                        request.getFio(), caseEntity.getNumber(), currentUserEmail),
                LogLevel.INFO,
                LogAction.FIGURANT_ADDED,
                caseEntity.getNumber(),
                currentUserEmail
        );
        return mapper.mapToFigurantResponse(saved);
    }

    @Override
    @Transactional
    public void removeUserFromCase(Long caseId, Long userId, String currentUserEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with id: " + caseId));

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + currentUserEmail));

        validateUserAccess(caseEntity, currentUser);

        User userToRemove = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        if (caseEntity.isOwner(userToRemove)) {
            logService.log(
                    String.format("User '%s' deleted from case №%s by user %s",
                            userToRemove.getEmail(), caseEntity.getNumber(), currentUserEmail),
                    LogLevel.ERROR,
                    LogAction.USER_DELETE,
                    caseEntity.getNumber(),
                    currentUserEmail
            );
            throw new IllegalStateException("Cannot remove the case owner");
        }

        if (!caseEntity.hasUser(userToRemove)) {
            logService.log(
                    String.format("User '%s' is not member from case №%s",
                            userToRemove.getEmail(), caseEntity.getNumber()),
                    LogLevel.ERROR,
                    LogAction.USER_DELETE,
                    caseEntity.getNumber(),
                    currentUserEmail
            );
            throw new IllegalStateException("User is not a member of this case");
        }
        String caseNumber = caseEntity.getNumber();

        caseEntity.removeUser(userToRemove);
        caseRepository.save(caseEntity);

        logService.log(
                String.format("User '%s' removed from case №%s by user %s",
                        userToRemove.getEmail(), caseNumber, currentUserEmail),
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

        validateUserAccess(caseEntity, currentUser);

        CaseFigurant figurant = caseEntity.getFigurants().stream()
                .filter(f -> f.getId().equals(figurantId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Figurant not found with id: " + figurantId));

        caseEntity.removeFigurant(figurant);
        caseRepository.save(caseEntity);

        logService.log(
                String.format("Figurant '%s' deleted from case №%s by user %s",
                        figurant.getFio(), caseEntity.getNumber(), currentUserEmail),
                LogLevel.INFO,
                LogAction.FIGURANT_DELETED,
                caseEntity.getNumber(),
                currentUserEmail
        );
        log.info("Figurant {} removed from case {} by {}", figurantId, caseId, currentUserEmail);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaseUserResponse> getCaseUsers(Long caseId, String currentUserEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with id: " + caseId));

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + currentUserEmail));

        validateUserAccess(caseEntity, currentUser);

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

        validateUserAccess(caseEntity, currentUser);

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

        validateUserAccess(caseEntity, currentUser);

        String caseNumber = caseEntity.getNumber();
        try {
            deleteAllFilesFromWorkspace(caseEntity.getNumber());

            minioService.deleteAllFilesFromCase(caseEntity.getNumber());

            taskQueueService.deleteTasksByCaseId(caseId);

            caseEntity.removeAllAttachedFiles();
            caseRepository.save(caseEntity);
            log.info("Deleted all files from case: {}", caseId);

            logService.log(
                    String.format("All files deleted from case №%s by user %s", caseNumber, currentUserEmail),
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

        validateUserAccess(caseEntity, currentUser);

        deleteAllFiles(caseId, currentUserEmail);

        caseRepository.delete(caseEntity);
        logService.log(
                String.format("Case '%s' deleted by user %s",
                        caseEntity.getNumber(), currentUserEmail),
                LogLevel.INFO,
                LogAction.CASE_DELETED,
                caseEntity.getNumber(),
                currentUserEmail
        );
        log.info("Case {} deleted", caseId);
    }

    @Transactional
    public void migrateAllCaseToms() {
        List<Case> allCases = caseRepository.findAll();

        for (Case caseEntity : allCases) {
            List<CaseFile> orderedFiles = caseEntity.getFiles().stream()
                    .sorted(Comparator.comparing(CaseFile::getOrderIndex,
                            Comparator.nullsLast(Integer::compareTo)))
                    .collect(Collectors.toList());

            for (int i = 0; i < orderedFiles.size(); i++) {
                if (orderedFiles.get(i).getOrderIndex() == null) {
                    orderedFiles.get(i).setOrderIndex(i);
                }
            }

            assignToms(orderedFiles, Collections.emptyList());
            caseFileRepository.saveAll(orderedFiles);

            log.info("Migrated toms for case: {}", caseEntity.getNumber());
        }
    }

    @Transactional
    public void recalculateAllPages() {
        List<CaseFile> files = caseFileRepository.findAll();
        int updated = 0;
        int failed = 0;

        for (CaseFile file : files) {
            try {
                Integer pages = pageCounter.countPagesByUrl(
                        file.getFileUrl(), file.getContentType());

                if (pages != null) {
                    file.setPages(pages);
                    caseFileRepository.save(file);
                    updated++;
                    log.debug("File {} — pages: {}", file.getOriginalFileName(), pages);
                } else {
                    log.debug("File {} — pages: null, skipping", file.getOriginalFileName());
                }
            } catch (Exception e) {
                failed++;
                log.warn("Failed to count pages for file {}: {}",
                        file.getOriginalFileName(), e.getMessage());
            }
        }

        log.info("Page count migration done. Updated: {}, Failed: {}, Skipped: {}",
                updated, failed, files.size() - updated - failed);
    }
}