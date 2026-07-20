package org.di.digital.service.impl.cases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.cases.CaseResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.model.enums.MessageConstant;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.LogService;
import org.di.digital.util.Mapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.di.digital.util.requests.UserUtil.validateOwnerAccess;
import static org.di.digital.util.requests.UserUtil.validateUserAccess;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseWriter {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final LogService logService;
    private final Mapper mapper;

    @Transactional(readOnly = true)
    public String authorizeForFileWipe(Long caseId, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Case not found with id: " + caseId));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));
        validateUserAccess(caseEntity, user);
        return caseEntity.getNumber();
    }

    @Transactional(readOnly = true)
    public String authorizeOwnerForDelete(Long caseId, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Case not found"));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        validateOwnerAccess(caseEntity, user);
        return caseEntity.getNumber();
    }

    @Transactional
    public String updateStatus(Long caseId, boolean status, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Case not found"));
        validateUserAccess(caseEntity, user);
        if (!caseEntity.isOwner(user)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Только создатель дела может изменять его статус");
        }
        caseEntity.setStatus(status);
        caseRepository.save(caseEntity);
        return caseEntity.getNumber();
    }

    @Transactional(readOnly = true)
    public EditPrecheck prepareEdit(Long caseId, String email, String newNumber) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));
        validateUserAccess(caseEntity, user);
        if (!caseEntity.isOwner(user)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Только создатель дела может редактировать его");
        }

        String oldNumber = caseEntity.getNumber();
        boolean numberChanges = newNumber != null && !newNumber.equals(oldNumber);
        if (numberChanges && caseRepository.existsByNumber(newNumber)) {
            logService.log(String.format("Case already exists: %s", newNumber),
                    LogLevel.ERROR, LogAction.CASE_UPDATED, newNumber, email);
            throw new IllegalStateException(MessageConstant.WORKSPACE_ALREADY_EXISTS.format(newNumber));
        }
        return new EditPrecheck(oldNumber, numberChanges);
    }

    @Transactional
    public CaseResponse applyEdit(Long caseId, String newNumber, String newTitle,
                                  boolean numberChanges, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));
        if (numberChanges) caseEntity.setNumber(newNumber);
        if (newTitle != null) caseEntity.setTitle(newTitle);
        Case saved = caseRepository.save(caseEntity);

        logService.log(String.format("Case %s edited by user %s", saved.getNumber(), email),
                LogLevel.INFO, LogAction.CASE_UPDATED, saved.getNumber(), email);
        return mapper.mapToCaseResponse(saved);
    }

    public record EditPrecheck(String oldNumber, boolean numberChanges) {}

    @Transactional
    public void wipeAttachedFiles(Long caseId, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseId));
        String caseNumber = caseEntity.getNumber();

        caseEntity.removeAllAttachedFiles();
        caseRepository.save(caseEntity);

        logService.log(String.format("All files deleted from case №%s by user %s", caseNumber, email),
                LogLevel.INFO, LogAction.FILE_DELETE, caseNumber, email);
    }

    @Transactional
    public void deleteCaseRecord(Long caseId, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Case not found"));
        String caseNumber = caseEntity.getNumber();

        caseRepository.delete(caseEntity);
        logService.log(String.format("Case '%s' deleted by user %s", caseNumber, email),
                LogLevel.INFO, LogAction.CASE_DELETED, caseNumber, email);
    }

    @Transactional(readOnly = true)
    public FileToDelete resolveFileForDeletion(Long caseId, String fileName, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseId));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));
        validateUserAccess(caseEntity, user);

        CaseFile file = caseEntity.getFiles().stream()
                .filter(f -> f.getOriginalFileName().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("File not found: " + fileName));

        if (CaseFileStatusEnum.PROCESSING.equals(file.getStatus())) {
            String message = MessageConstant.CANNOT_DELETE_FILE.format(caseEntity.getNumber());
            logService.log(String.format("Cannot delete processing file in case %s", caseEntity.getNumber()),
                    LogLevel.ERROR, LogAction.FILE_DELETE, caseEntity.getNumber(), email);
            throw new IllegalStateException(message);
        }

        return new FileToDelete(file.getId(), file.getFileUrl(),
                file.getOriginalFileName(), caseEntity.getNumber(),
                CaseFileStatusEnum.COMPLETED.equals(file.getStatus()));
    }

    @Transactional
    public void removeFileRecord(Long caseId, Long fileId, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseId));
        caseEntity.getFiles().removeIf(f -> f.getId().equals(fileId));
        caseRepository.save(caseEntity);
    }

    public record FileToDelete(Long id, String fileUrl, String originalFileName,
                               String caseNumber, boolean wasCompleted) {}
}