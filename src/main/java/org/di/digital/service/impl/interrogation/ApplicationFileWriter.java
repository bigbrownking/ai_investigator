package org.di.digital.service.impl.interrogation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.interrogation.CaseInterrogationApplicationFileResponse;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.enums.file.CaseFileStatusEnum;
import org.di.digital.model.enums.log.LogAction;
import org.di.digital.model.enums.log.LogLevel;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.interrogation.CaseInterrogationApplicationFile;
import org.di.digital.model.user.User;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.LogService;
import org.di.digital.service.cases.CaseAccessService;
import org.di.digital.service.impl.queue.TaskQueueService;
import org.di.digital.util.mapper.InterrogationMapper;
import org.di.digital.util.requests.UserUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static org.di.digital.util.requests.UserUtil.getCurrentLang;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationFileWriter {

    private final CaseInterrogationRepository caseInterrogationRepository;
    private final TaskQueueService taskQueueService;
    private final LogService logService;
    private final InterrogationMapper mapper;
    private final InterrogationAuthService interrogationAuthService;

    // ---- Фаза 1: валидация + собрать контекст для загрузки ----
    @Transactional(readOnly = true)
    public UploadContext prepare(Long caseId, Long interrogationId, String email) {
        InterrogationAuthService.AuthorizedInterrogation ctx = interrogationAuthService.loadAndAuthorize(caseId, interrogationId, email,
                CaseModule.DOCUMENTS, CaseAction.ADD);

        CaseInterrogation interrogation = ctx.interrogation();
        Case caseEntity = interrogation.getCaseEntity();

        Set<String> existingInInterrogation = interrogation.getApplicationFiles().stream()
                .map(CaseInterrogationApplicationFile::getOriginalFileName)
                .collect(Collectors.toCollection(HashSet::new));

        Set<String> existingInCase = caseEntity.getFiles().stream()
                .map(CaseFile::getOriginalFileName)
                .collect(Collectors.toCollection(HashSet::new));

        return new UploadContext(
                caseEntity.getId(), caseEntity.getNumber(),
                interrogation.getFio(), interrogation.getAdequateLanguage(),
                existingInInterrogation, existingInCase);
    }

    // ---- Фаза 3: привязать загруженные файлы, сохранить, поставить в очередь ----
    @Transactional
    public List<CaseInterrogationApplicationFileResponse> persist(
            Long interrogationId, String email, String language,
            List<UploadedFile> uploaded) {

        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.INTERROGATION.localized(currentLang(), interrogationId.toString())));
        Case caseEntity = interrogation.getCaseEntity();

        // перечитываем актуальные множества внутри транзакции (могли измениться)
        Set<String> existingInInterrogation = interrogation.getApplicationFiles().stream()
                .map(CaseInterrogationApplicationFile::getOriginalFileName)
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> existingInCase = caseEntity.getFiles().stream()
                .map(CaseFile::getOriginalFileName)
                .collect(Collectors.toCollection(HashSet::new));

        List<CaseFile> newCaseFiles = new ArrayList<>();

        for (UploadedFile uf : uploaded) {
            String originalName = uf.originalFileName();
            if (existingInInterrogation.contains(originalName)) {
                log.warn("File already exists in interrogation {} at persist: {}", interrogationId, originalName);
                continue;
            }

            CaseInterrogationApplicationFile appFile = CaseInterrogationApplicationFile.builder()
                    .originalFileName(uf.originalFileName())
                    .storedFileName(uf.storedFileName())
                    .fileUrl(uf.fileUrl())
                    .contentType(uf.contentType())
                    .fileSize(uf.fileSize())
                    .uploadedAt(uf.uploadedAt())
                    .displayName(uf.displayName())
                    .pages(uf.pages())
                    .build();
            appFile.addInterrogation(interrogation);
            existingInInterrogation.add(originalName);

            if (!existingInCase.contains(originalName)) {
                CaseFile caseFile = CaseFile.builder()
                        .originalFileName(uf.originalFileName())
                        .storedFileName(uf.storedFileName())
                        .fileUrl(uf.fileUrl())
                        .contentType(uf.contentType())
                        .fileSize(uf.fileSize())
                        .uploadedAt(uf.uploadedAt())
                        .pages(uf.pages())
                        .status(CaseFileStatusEnum.QUEUED)
                        .isQualification(false)
                        .build();
                caseFile.addCaseEntity(caseEntity);
                newCaseFiles.add(caseFile);
                existingInCase.add(originalName);
            }
        }

        caseInterrogationRepository.saveAndFlush(interrogation);

        // задачи в очередь — после flush, чтобы у caseFile был id
        for (CaseFile caseFile : newCaseFiles) {
            taskQueueService.addTaskToQueue(
                    email, caseEntity.getId(), caseEntity.getNumber(),
                    caseFile.getOriginalFileName(), caseFile.getFileUrl(), caseFile.getId(), language);
        }

        List<CaseInterrogationApplicationFileResponse> result = interrogation.getApplicationFiles().stream()
                .map(mapper::toApplicationFileResponse)
                .toList();

        String fileNames = uploaded.stream()
                .map(UploadedFile::originalFileName)
                .collect(Collectors.joining(", "));
        logService.log(
                String.format("Uploading application files [%s] to interrogation №%d (FIO: %s) by user %s in case №%s",
                        fileNames, interrogationId, interrogation.getFio(), email, caseEntity.getNumber()),
                LogLevel.INFO, LogAction.FILE_UPLOAD, caseEntity.getNumber(), email);

        return result;
    }

    // контекст фазы 1
    public record UploadContext(
            Long caseEntityId, String caseNumber, String fio, String language,
            Set<String> existingInInterrogation, Set<String> existingInCase) {}

    // одна загруженная единица (результат фазы 2)
    public record UploadedFile(
            String originalFileName, String storedFileName, String fileUrl,
            String contentType, Long fileSize, java.time.LocalDateTime uploadedAt,
            String displayName, Integer pages) {}

    private UserSettingsLanguage currentLang(){
        return getCurrentLang();
    }
}