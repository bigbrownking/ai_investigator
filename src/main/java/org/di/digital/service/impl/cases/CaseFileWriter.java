package org.di.digital.service.impl.cases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.cases.CaseFileResponse;
import org.di.digital.dto.response.cases.CaseResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.enums.*;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.LogService;
import org.di.digital.service.impl.queue.TaskQueueService;
import org.di.digital.util.Mapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.di.digital.util.requests.UserUtil.validateUserAccess;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseFileWriter {

    private static final int MAX_PAGES_PER_TOM = 180;

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseFileRepository caseFileRepository;
    private final TaskQueueService taskQueueService;
    private final LogService logService;
    private final Mapper mapper;


    @Transactional
    public CreatedCase createCaseShell(CreateCaseData data, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));

        if (caseRepository.existsByNumber(data.number())) {
            logService.log(String.format("Case already exists: %s", data.number()),
                    LogLevel.ERROR, LogAction.CASE_CREATED, data.number(), user.getEmail());
            throw new IllegalStateException("Дело уже создано, пожалуйста проинформируйте создателя дела.");
        }

        Case newCase = Case.builder()
                .title(data.title())
                .number(data.number())
                .language(data.language())
                .files(new ArrayList<>())
                .owner(user)
                .users(new HashSet<>())
                .build();
        newCase.addUser(user);

        Case saved = caseRepository.saveAndFlush(newCase);
        return new CreatedCase(saved.getId(), saved.getNumber());
    }

    @Transactional
    public CaseResponse attachFilesToNewCase(Long caseId, String email, String language,
                                             List<UploadedFile> uploaded) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseId));

        List<CaseFile> newFiles = new ArrayList<>();
        for (UploadedFile uf : uploaded) {
            CaseFile caseFile = buildCaseFile(uf, language, false);
            caseFile.addCaseEntity(caseEntity);
            newFiles.add(caseFile);
        }

        assignToms(newFiles, Collections.emptyList());
        caseRepository.flush();

        enqueueTasks(caseEntity, newFiles, email, language);

        logService.log(String.format("Case %s created by user %s", caseEntity.getNumber(), email),
                LogLevel.INFO, LogAction.CASE_CREATED, caseEntity.getNumber(), email);

        return mapper.mapToCaseResponse(caseEntity);
    }


    @Transactional(readOnly = true)
    public AddFilesContext prepareAddFiles(Long caseId, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseId));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("user not found: " + email));
        validateUserAccess(caseEntity, user);

        if (Boolean.TRUE.equals(caseEntity.getIsFinalIndictmentDone())) {
            logService.log(String.format("Cannot upload files by %s user in case %s",
                            email, caseEntity.getNumber()),
                    LogLevel.ERROR, LogAction.FILE_UPLOAD, caseEntity.getNumber(), email);
            throw new IllegalStateException(
                    MessageConstant.CANNOT_UPLOAD_FILE.format(caseEntity.getNumber()));
        }

        Set<String> existingNames = caseEntity.getFiles().stream()
                .map(CaseFile::getOriginalFileName)
                .collect(Collectors.toCollection(HashSet::new));

        return new AddFilesContext(caseEntity.getNumber(), caseEntity.getLanguage(), existingNames);
    }

    @Transactional
    public List<CaseFileResponse> attachFilesToExistingCase(Long caseId, String email,
                                                            String language, boolean isQualification,
                                                            List<UploadedFile> uploaded) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseId));

        List<CaseFile> existingFiles = new ArrayList<>(caseEntity.getFiles());
        Set<String> existingNames = existingFiles.stream()
                .map(CaseFile::getOriginalFileName)
                .collect(Collectors.toCollection(HashSet::new));

        List<CaseFile> newFiles = new ArrayList<>();
        for (UploadedFile uf : uploaded) {
            if (existingNames.contains(uf.originalFileName())) {
                log.warn("File already exists at persist: {} in case {}", uf.originalFileName(), caseId);
                continue;
            }
            CaseFile caseFile = buildCaseFile(uf, language, isQualification);
            caseFile.addCaseEntity(caseEntity);
            newFiles.add(caseFile);
            existingNames.add(uf.originalFileName());
        }

        assignToms(newFiles, existingFiles);

        List<CaseFile> savedFiles = caseFileRepository.saveAllAndFlush(newFiles);

        enqueueTasks(caseEntity, savedFiles, email, language);

        String fileNames = savedFiles.stream()
                .map(CaseFile::getOriginalFileName)
                .collect(Collectors.joining(", "));
        logService.log(String.format("Uploading files by %s user in case %s: [%s]",
                        email, caseEntity.getNumber(), fileNames),
                LogLevel.INFO, LogAction.FILE_UPLOAD, caseEntity.getNumber(), email);

        return savedFiles.stream().map(mapper::mapToCaseFileResponse).toList();
    }


    private CaseFile buildCaseFile(UploadedFile uf, String language, boolean isQualification) {
        return CaseFile.builder()
                .originalFileName(uf.originalFileName())
                .storedFileName(uf.storedFileName())
                .fileUrl(uf.fileUrl())
                .contentType(uf.contentType())
                .fileSize(uf.fileSize())
                .uploadedAt(uf.uploadedAt())
                .pages(uf.pages())
                .status(CaseFileStatusEnum.QUEUED)
                .isQualification(isQualification)
                .language(language)
                .build();
    }

    private void enqueueTasks(Case caseEntity, List<CaseFile> files, String email, String language) {
        for (CaseFile caseFile : files) {
            taskQueueService.addTaskToQueue(
                    email, caseEntity.getId(), caseEntity.getNumber(),
                    caseFile.getOriginalFileName(), caseFile.getFileUrl(),
                    caseFile.getId(), language);
        }
    }

    private void assignToms(List<CaseFile> newFiles, List<CaseFile> existingFiles) {
        Map<Integer, Integer> tomPageCounts = new HashMap<>();
        for (CaseFile f : existingFiles) {
            int t = f.getTom() == null ? 1 : f.getTom();
            int pages = f.getPages() == null ? 0 : f.getPages();
            tomPageCounts.merge(t, pages, Integer::sum);
        }
        int currentTom = tomPageCounts.isEmpty() ? 1 : Collections.max(tomPageCounts.keySet());
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

    public record CreateCaseData(String title, String number, String language) {}
    public record CreatedCase(Long id, String number) {}
    public record AddFilesContext(String caseNumber, String language, Set<String> existingNames) {}
    public record UploadedFile(
            String originalFileName, String storedFileName, String fileUrl,
            String contentType, Long fileSize, LocalDateTime uploadedAt, Integer pages) {}
}