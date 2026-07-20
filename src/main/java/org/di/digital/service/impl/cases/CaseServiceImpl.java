package org.di.digital.service.impl.cases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.cases.ChangeCaseLanguageRequest;
import org.di.digital.dto.request.cases.ReorderCaseFilesRequest;
import org.di.digital.dto.response.cases.*;
import org.di.digital.dto.response.interrogation.FigurantResponse;
import org.di.digital.dto.response.user.UserSuggestionResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.cases.CaseMemberHistory;
import org.di.digital.model.enums.*;
import org.di.digital.dto.request.interrogation.AddFigurantToCaseRequest;
import org.di.digital.dto.request.cases.CreateCaseRequest;
import org.di.digital.dto.request.cases.EditCaseRequest;
import org.di.digital.model.interrogation.CaseFigurant;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.repository.cases.CaseMemberHistoryRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.CaseService;
import org.di.digital.service.LogService;
import org.di.digital.service.MinioService;
import org.di.digital.service.impl.core.DevService;
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

import static org.di.digital.util.requests.RequestUrlBuilder.deleteAllDocumentsUrl;
import static org.di.digital.util.requests.RequestUrlBuilder.deleteDocumentUrl;
import static org.di.digital.util.requests.UserUtil.validateOwnerAccess;
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
    private final DevService devService;
    private final CaseFileWriter caseFileWriter;
    private final CaseWriter caseWriter;
    private final CaseMemberHistoryRepository caseMemberHistoryRepository;

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
    public CaseResponse createCase(CreateCaseRequest request, String email) {
        CaseFileWriter.CreatedCase created = caseFileWriter.createCaseShell(
                new CaseFileWriter.CreateCaseData(
                        request.getTitle(), request.getNumber(), request.getLanguage()),
                email);

        List<CaseFileWriter.UploadedFile> uploaded = Collections.emptyList();
        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            uploaded = uploadAndCount(request.getFiles(), created.number(), Collections.emptySet());
        }

        CaseResponse response = caseFileWriter.attachFilesToNewCase(
                created.id(), email, request.getLanguage(), uploaded);

        log.info("Case created with id: {} for user: {}", created.id(), email);
        return response;
    }

    @Override
    public CaseResponse editCase(Long caseId, EditCaseRequest request, String email) {
        CaseWriter.EditPrecheck pre =
                caseWriter.prepareEdit(caseId, email, request.getNumber());

        if (pre.numberChanges()) {
            renameWorkspace(pre.oldNumber(), request.getNumber());
        }

        CaseResponse response = caseWriter.applyEdit(
                caseId, request.getNumber(), request.getTitle(), pre.numberChanges(), email);

        log.info("Case {} edited by user: {}", caseId, email);
        return response;
    }


    @Override
    public CaseResponse changeCaseLanguage(Long caseId, ChangeCaseLanguageRequest request, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));

        validateUserAccess(caseEntity, user);

        if (!caseEntity.isOwner(user)) {
            throw new AccessDeniedException("Только создатель дела может редактировать его");
        }

        String fromLanguage = caseEntity.getLanguage();
        if(request.getLanguage() != null){
            caseEntity.setLanguage(request.getLanguage());
        }

        Case savedCase = caseRepository.save(caseEntity);

        log.info("Case {} edited by user: {}", caseId, email);

        logService.log(
                String.format("Case %s has changed its language from %s to %s by user %s", savedCase.getNumber(),fromLanguage, request.getLanguage(),email),
                LogLevel.INFO,
                LogAction.CASE_LANGUAGE_UPDATE,
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
                                .thenComparing(CaseFile::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                                .thenComparing(CaseFile::getUploadedAt, Comparator.nullsLast(Comparator.naturalOrder())))
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
    public List<CasePreviewResponse> getUserCases(String email, String sort) {
        List<CasePreviewResponse> previews = caseRepository.findPreviewsForUser(email);

        Comparator<CasePreviewResponse> cmp =
                Comparator.comparing(CasePreviewResponse::getCreatedDate,
                        "asc".equalsIgnoreCase(sort)
                                ? Comparator.nullsLast(Comparator.naturalOrder())
                                : Comparator.nullsLast(Comparator.reverseOrder()));

        return previews.stream().sorted(cmp).collect(Collectors.toList());
    }

    @Override
    public void updateCaseStatus(Long caseId, boolean status, String email) {
        log.info("Updating status for case: {} to {} by user: {}", caseId, status, email);

        String caseNumber = caseWriter.updateStatus(caseId, status, email);

        devService.setCasePriority(caseNumber, status ? 0 : -1);

        log.info("Case {} status updated to {}", caseId, status);
        logService.log(String.format("Case %s status updated to %s by user %s", caseNumber, status, email),
                LogLevel.INFO, LogAction.CASE_STATUS_CHANGED, caseNumber, email);
    }

    public List<CaseFileResponse> addFilesToCase(Long caseId, List<MultipartFile> files,
                                                 FileType type, String email) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        CaseFileWriter.AddFilesContext ctx = caseFileWriter.prepareAddFiles(caseId, email);


        List<CaseFileWriter.UploadedFile> uploaded =
                uploadAndCount(files, ctx.caseNumber(), ctx.existingNames());

        if (uploaded.isEmpty()) {
            return Collections.emptyList();
        }

        boolean isQualification = type == FileType.QUALIFICATION;
        List<CaseFileResponse> result = caseFileWriter.attachFilesToExistingCase(
                caseId, email, ctx.language(), isQualification, uploaded);

        log.info("Added {}/{} files to case: {}", uploaded.size(), files.size(), caseId);
        return result;
    }


    @Transactional
    public GroupedCaseFileResponse reorderCaseFiles(Long caseId, ReorderCaseFilesRequest request,
                                                    String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseId));

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


    @Override
    public void deleteFileFromCase(Long caseId, String fileName, String email) {
        CaseWriter.FileToDelete f = caseWriter.resolveFileForDeletion(caseId, fileName, email);

        try {
            if (f.wasCompleted()) {
                deleteFileFromWorkspace(f.caseNumber(), fileName);
            }
            minioService.deleteFile(f.fileUrl());
            taskQueueService.deleteTask(f.id());

            caseWriter.removeFileRecord(caseId, f.id(), email);

            logService.log(String.format("Deleted %s file from case %s", f.originalFileName(), f.caseNumber()),
                    LogLevel.INFO, LogAction.FILE_DELETE, f.caseNumber(), email);
            log.info("Deleted file: {} from case: {}", fileName, caseId);

        } catch (Exception e) {
            log.error("Failed to delete file: {} from case: {}", fileName, caseId, e);
            throw new RuntimeException("Failed to delete file: " + fileName, e);
        }
    }

    private void deleteFileFromWorkspace(String caseNumber, String fileName) {
        String url = deleteDocumentUrl(pythonHost, pythonPort, caseNumber, fileName);

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
        String url = deleteAllDocumentsUrl(pythonHost, pythonPort, caseNumber);

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
    public CaseUserResponse addUserToCase(Long caseId, Long id, String currentUserEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with id: " + caseId));

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + currentUserEmail));

        validateOwnerAccess(caseEntity, currentUser);

        User userToAdd = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        String email = userToAdd.getEmail();
        if (caseEntity.hasUser(userToAdd)) {
            logService.log(
                    String.format("User '%s' already added to case №%s by user %s",
                            email, caseEntity.getNumber(), currentUserEmail),
                    LogLevel.ERROR,
                    LogAction.USER_ADD,
                    caseEntity.getNumber(),
                    currentUserEmail
            );
            throw new IllegalStateException("Следователь уже есть в деле!");
        }

        String caseNumber = caseEntity.getNumber();
        caseEntity.addUser(userToAdd);
        Case savedCase = caseRepository.save(caseEntity);

        recordMemberHistory(caseNumber, userToAdd, currentUser, CaseMemberAction.ADD);

        log.info("User {} added to case {} by {}", email, caseId, currentUserEmail);

        logService.log(
                String.format("User '%s' added to case №%s by user %s",
                        email, caseNumber, currentUserEmail),
                LogLevel.INFO,
                LogAction.USER_ADD,
                caseNumber,
                currentUserEmail
        );
        return mapper.mapToCaseUserResponse(userToAdd, savedCase);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaseMemberHistoryDto> getMemberHistory(Long caseId, String currentUserEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with id: " + caseId));

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + currentUserEmail));

        validateOwnerAccess(caseEntity, currentUser);

        return caseMemberHistoryRepository
                .findByCaseNumberOrderByTimestampDesc(caseEntity.getNumber())
                .stream()
                .map(mapper::toCaseMemberHistoryDto)
                .toList();
    }

    private void recordMemberHistory(String caseNumber, User target,
                                     User performedBy, CaseMemberAction action) {
        caseMemberHistoryRepository.save(CaseMemberHistory.builder()
                .caseNumber(caseNumber)
                .action(action)
                .targetEmail(target.getEmail())
                .targetFio(target.getFio())
                .performedByEmail(performedBy.getEmail())
                .performedByFio(performedBy.getFio())
                .build());
    }

    @Override
    public List<UserSuggestionResponse> searchUsers(String query) {

        return userRepository.searchAllUsers(query)
                .stream()
                .map(user -> UserSuggestionResponse.builder()
                        .id(user.getId())
                        .fio(String.join(" ",
                                        Optional.ofNullable(user.getSurname()).orElse(""),
                                        Optional.ofNullable(user.getName()).orElse(""),
                                        Optional.ofNullable(user.getFathername()).orElse(""))
                                .trim()
                                .replaceAll("\\s+", " "))
                        .email(user.getEmail())
                        .build())
                .toList();
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

        validateOwnerAccess(caseEntity, currentUser);

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

        recordMemberHistory(caseNumber, userToRemove, currentUser, CaseMemberAction.REMOVE);

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
        // 1) короткая tx: валидация + caseNumber
        String caseNumber = caseWriter.authorizeForFileWipe(caseId, currentUserEmail);

        try {
            deleteAllFilesFromWorkspace(caseNumber);
            minioService.deleteAllFilesFromCase(caseNumber);
            taskQueueService.deleteTasksByCaseId(caseId);

            caseWriter.wipeAttachedFiles(caseId, currentUserEmail);
            log.info("Deleted all files from case: {}", caseId);

        } catch (Exception e) {
            log.error("Failed to delete all files from case: {}", caseId, e);
            throw new RuntimeException("Failed to delete all files: ", e);
        }
    }

    @Override
    public void deleteCaseById(Long caseId, String currentUserEmail) {
        caseWriter.authorizeOwnerForDelete(caseId, currentUserEmail);

        deleteAllFiles(caseId, currentUserEmail);

        caseWriter.deleteCaseRecord(caseId, currentUserEmail);
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

    private List<CaseFileWriter.UploadedFile> uploadAndCount(
            List<MultipartFile> files, String caseNumber, Set<String> alreadyExisting) {

        List<CaseFileWriter.UploadedFile> uploaded = new ArrayList<>();
        Set<String> seen = new HashSet<>(alreadyExisting);

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String originalName = file.getOriginalFilename();
            if (seen.contains(originalName)) {
                log.warn("File already exists: {} in case {}", originalName, caseNumber);
                continue;
            }
            try {
                CaseFile caseFile = minioService.uploadFile(file, caseNumber);

                Integer pages = null;
                try {
                    pages = pageCounter.countPagesByUrl(caseFile.getFileUrl(), caseFile.getContentType());
                } catch (Exception e) {
                    log.warn("Could not count pages for {}: {}", caseFile.getOriginalFileName(), e.getMessage());
                }

                uploaded.add(new CaseFileWriter.UploadedFile(
                        caseFile.getOriginalFileName(), caseFile.getStoredFileName(), caseFile.getFileUrl(),
                        caseFile.getContentType(), caseFile.getFileSize(), caseFile.getUploadedAt(), pages));
                seen.add(originalName);

            } catch (Exception e) {
                log.error("Failed to upload file: {} to case {}", originalName, caseNumber, e);
            }
        }
        return uploaded;
    }

    @Override
    @Transactional(readOnly = true)
    public CaseFileResponse getFileByName(Long caseId, String fileName, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));

        validateUserAccess(caseEntity, user);

        CaseFile caseFile = caseFileRepository
                .findByOriginalFileNameAndCaseEntityId(fileName, caseId)
                .orElseThrow(() -> new IllegalStateException("Файл не найден: " + fileName));

        return mapper.mapToCaseFileResponse(caseFile);
    }
}