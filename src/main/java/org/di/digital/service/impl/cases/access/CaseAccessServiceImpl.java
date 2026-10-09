package org.di.digital.service.impl.cases.access;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.cases.access.GrantAccessRequest;
import org.di.digital.dto.request.cases.access.UpdateFileAccessRequest;
import org.di.digital.dto.response.access.FileGrantDto;
import org.di.digital.dto.response.access.MyAccessDto;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.AccessDeniedMessage;
import org.di.digital.exception.message.IllegalStateMessage;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.cases.*;
import org.di.digital.model.enums.MessageConstant;
import org.di.digital.model.enums.log.LogAction;
import org.di.digital.model.enums.log.LogLevel;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseMemberType;
import org.di.digital.model.enums.permission.CaseModule;
import org.di.digital.model.enums.permission.DocumentAccessScope;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.user.User;
import org.di.digital.repository.assess.CaseFileAccessRepository;
import org.di.digital.repository.assess.CaseUserAccessRepository;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.LogService;
import org.di.digital.service.cases.CaseAccessService;
import org.di.digital.util.mapper.PermissionMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.di.digital.util.requests.UserUtil.getCurrentLang;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseAccessServiceImpl implements CaseAccessService {
    private final CaseUserAccessRepository accessRepository;
    private final CaseFileAccessRepository fileAccessRepository;
    private final CaseFileRepository caseFileRepository;
    private final PermissionMapper permissionMapper;
    private final UserRepository userRepository;
    private final CaseRepository caseRepository;
    private final LogService logService;

    private static final Map<CaseModule, Set<CaseAction>> SOG_PERMISSIONS = Map.of(
            CaseModule.CASE,          EnumSet.of(CaseAction.READ),
            CaseModule.INTERROGATION, EnumSet.allOf(CaseAction.class),
            CaseModule.CHAT,          EnumSet.allOf(CaseAction.class),
            CaseModule.DOCUMENTS,     EnumSet.of(CaseAction.READ, CaseAction.DOWNLOAD)
    );

    private static final Set<CaseAction> SOG_FILE_ACTIONS =
            EnumSet.of(CaseAction.READ, CaseAction.DOWNLOAD);

    @Transactional
    public void grantInitialAccess(Case caseEntity, User user, List<FileGrantDto> fileGrants) {
        if (caseEntity.isOwner(user)
                || accessRepository.existsByCaseEntityIdAndUserId(caseEntity.getId(), user.getId())) {
            return;
        }
        boolean restricted = fileGrants != null && !fileGrants.isEmpty();
        DocumentAccessScope scope = restricted
                ? DocumentAccessScope.RESTRICTED
                : DocumentAccessScope.ALL;

        CaseUserAccess access = CaseUserAccess.builder()
                .caseEntity(caseEntity)
                .user(user)
                .permissions(defaultPermissions())
                .documentScope(scope)
                .build();
        accessRepository.save(access);
        logService.log(
                String.format("User %s granted initial access", user.getEmail()),
                LogLevel.INFO,
                LogAction.INITIAL_ACCESS,
                caseEntity.getNumber(),
                user.getEmail()
        );
        if (restricted) {
            grantFiles(caseEntity, user, fileGrants);
        }
    }
    @Transactional
    public void grantFullAccess(Case caseEntity, User user) {
        CaseUserAccess access = accessRepository
                .findByCaseEntityIdAndUserId(caseEntity.getId(), user.getId())
                .orElseGet(() -> CaseUserAccess.builder()
                        .caseEntity(caseEntity)
                        .user(user)
                        .permissions(new HashSet<>())
                        .build());

        access.setPermissions(allPermissions());
        access.setDocumentScope(DocumentAccessScope.ALL);
        access.setMemberType(CaseMemberType.MEMBER);
        accessRepository.save(access);

        fileAccessRepository.deleteAllActionsByCaseAndUser(caseEntity.getId(), user.getId());
        fileAccessRepository.deleteAllByCaseAndUser(caseEntity.getId(), user.getId());

        logService.log(
                String.format("User %s granted full access", user.getEmail()),
                LogLevel.INFO,
                LogAction.FULL_ACCESS,
                caseEntity.getNumber(),
                user.getEmail()
        );
    }

    private Set<CasePermission> allPermissions() {
        Set<CasePermission> perms = new HashSet<>();
        for (CaseModule module : CaseModule.values()) {
            for (CaseAction action : CaseAction.values()) {
                perms.add(CasePermission.builder()
                        .module(module).action(action).build());
            }
        }
        return perms;
    }
    @Transactional
    public void grantFiles(Case caseEntity, User user, List<FileGrantDto> fileGrants) {
        if (fileGrants == null) return;
        for (FileGrantDto grant : fileGrants) {
            CaseFile file = caseFileRepository.findById(grant.fileId())
                    .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang())));
            if (!file.getCaseEntity().getId().equals(caseEntity.getId())) {
                throw new IllegalStateException(MessageConstant.FILE_NOT_BELONG_TO_CASE.localized(currentLang(), file.getId().toString()));
            }
            CaseFileAccess fa = fileAccessRepository
                    .findByFileIdAndUserId(file.getId(), user.getId())
                    .orElseGet(() -> CaseFileAccess.builder()
                            .file(file).user(user).actions(new HashSet<>()).build());
            fa.setActions(new HashSet<>(grant.actions()));
            fileAccessRepository.save(fa);
        }
    }

    @Transactional
    public void revokeFile(Long fileId, Long userId) {
        fileAccessRepository.deleteByFileIdAndUserId(fileId, userId);
    }
    @Transactional
    public void revokeAll(Long caseId, Long userId) {
        accessRepository.deleteByCaseEntityIdAndUserId(caseId, userId);
        fileAccessRepository.deleteAllActionsByCaseAndUser(caseId, userId);
        fileAccessRepository.deleteAllByCaseAndUser(caseId, userId);
    }
    private Set<CasePermission> defaultPermissions() {
        Set<CasePermission> perms = new HashSet<>();
        for (CaseModule module : CaseModule.values()) {
            perms.add(CasePermission.builder()
                    .module(module).action(CaseAction.READ).build());
        }
        return perms;
    }
    public boolean can(Case caseEntity, User user, CaseModule module, CaseAction action) {
        if (caseEntity.isOwner(user)) return true;
        return accessRepository.findByCaseEntityIdAndUserId(caseEntity.getId(), user.getId())
                .map(a -> a.can(module, action))
                .orElse(false);
    }

    public void require(Case caseEntity, User user, CaseModule module, CaseAction action) {
        if (!can(caseEntity, user, module, action)) {
            UserSettingsLanguage lang = getCurrentLang();
            String detail = module.localized(lang) + " / " + action.localized(lang);
            logService.log(
                    String.format("User %s tried to access case %s", user.getEmail(), caseEntity.getNumber()),
                    LogLevel.WARNING,
                    LogAction.NO_ACCESS,
                    caseEntity.getNumber(),
                    user.getEmail()
            );
            throw new AccessDeniedException(AccessDeniedMessage.USER_ONLY.localized(lang, detail));
        }
    }
    public boolean canAccessFile(CaseFile file, User user, CaseAction action) {
        Case caseEntity = file.getCaseEntity();
        if (caseEntity.isOwner(user)) return true;

        CaseUserAccess access = accessRepository
                .findByCaseEntityIdAndUserId(caseEntity.getId(), user.getId())
                .orElse(null);
        if (access == null) return false;
        if (!access.can(CaseModule.DOCUMENTS, action)) return false;
        if (access.getDocumentScope() == DocumentAccessScope.ALL) return true;

        return fileAccessRepository.findByFileIdAndUserId(file.getId(), user.getId())
                .map(fa -> fa.getActions().contains(action))
                .orElse(false);
    }

    public void requireFile(CaseFile file, User user, CaseAction action) {
        if (!canAccessFile(file, user, action)) {
            UserSettingsLanguage lang = getCurrentLang();
            String detail = file.getId() + " / " + action.localized(lang);
            throw new AccessDeniedException(AccessDeniedMessage.USER_ONLY.localized(lang, detail));
        }
    }
    public MyAccessDto getMyPermissions(Case caseEntity, User user) {
        if (caseEntity.isOwner(user)) {
            Map<CaseModule, Set<CaseAction>> full = new EnumMap<>(CaseModule.class);
            for (CaseModule m : CaseModule.values()) {
                full.put(m, EnumSet.allOf(CaseAction.class));
            }
            return MyAccessDto.builder()
                    .permissions(full)
                    .documentScope(DocumentAccessScope.ALL)
                    .fileGrants(null)
                    .build();
        }

        return accessRepository.findByCaseEntityIdAndUserId(caseEntity.getId(), user.getId())
                .map(a -> {
                    List<FileGrantDto> fileGrants = null;
                    if (a.getDocumentScope() == DocumentAccessScope.RESTRICTED) {
                        fileGrants = fileAccessRepository
                                .findByFileIdInAndUserId(
                                        caseEntity.getFiles().stream()
                                                .map(CaseFile::getId)
                                                .toList(),
                                        user.getId())
                                .stream()
                                .map(fa -> new FileGrantDto(fa.getFile().getId(), fa.getActions()))
                                .toList();
                    }
                    return MyAccessDto.builder()
                            .permissions(permissionMapper.groupAsMap(a.getPermissions()))
                            .documentScope(a.getDocumentScope())
                            .fileGrants(fileGrants)
                            .build();
                })
                .orElse(MyAccessDto.builder()
                        .permissions(Map.of())
                        .documentScope(DocumentAccessScope.RESTRICTED)
                        .fileGrants(List.of())
                        .build());
    }

    @Transactional
    public void grantAccess(Long caseId, Long userId, GrantAccessRequest request, String ownerEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseId.toString())));
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), ownerEmail)));

        if (!caseEntity.isOwner(owner)) {
            throw new AccessDeniedException(AccessDeniedMessage.OWNER_ONLY.localized(currentLang()));        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), userId.toString())));

        if (caseEntity.isOwner(target)) {
            throw new IllegalStateException(IllegalStateMessage.INVALID_OPERATION.localized(currentLang()));
        }

        CaseUserAccess access = accessRepository
                .findByCaseEntityIdAndUserId(caseId, target.getId())
                .orElseGet(() -> CaseUserAccess.builder()
                        .caseEntity(caseEntity)
                        .user(target)
                        .permissions(new HashSet<>())
                        .documentScope(DocumentAccessScope.ALL)
                        .build());

        access.setPermissions(permissionMapper.flatten(request.getPermissions()));
        access.setDocumentScope(request.getDocumentScope() != null
                ? request.getDocumentScope()
                : DocumentAccessScope.ALL);

        accessRepository.save(access);

        if (access.getDocumentScope() == DocumentAccessScope.RESTRICTED
                && request.getFileGrants() != null) {
            fileAccessRepository.deleteAllActionsByCaseAndUser(caseId, target.getId());
            fileAccessRepository.deleteAllByCaseAndUser(caseId, target.getId());
            grantFiles(caseEntity, target, request.getFileGrants());
        } else if (access.getDocumentScope() == DocumentAccessScope.ALL) {
            fileAccessRepository.deleteAllActionsByCaseAndUser(caseId, target.getId());
            fileAccessRepository.deleteAllByCaseAndUser(caseId, target.getId());
        }

        log.info("Access granted to user {} in case {} by {}", target.getEmail(), caseId, ownerEmail);
        logService.log(
                String.format("User %s granted access to case %s by %s", target.getEmail(), caseEntity.getNumber(), ownerEmail),
                LogLevel.WARNING,
                LogAction.GRANT_ACCESS,
                caseEntity.getNumber(),
                owner.getEmail()
        );
    }

    @Transactional
    public void revokeAccess(Long caseId, Long userId, String ownerEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseId.toString())));
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), userId.toString())));

        if (!caseEntity.isOwner(owner)) {
            throw new AccessDeniedException(AccessDeniedMessage.OWNER_ONLY.localized(currentLang()));
        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), userId.toString())));

        if (caseEntity.isOwner(target)) {
            throw new IllegalStateException(IllegalStateMessage.INVALID_OPERATION.localized(currentLang()));
        }

        revokeAll(caseId, userId);
        log.info("Access revoked for user {} in case {} by {}", userId, caseId, ownerEmail);
        logService.log(
                String.format("User %s revoked access to case %s by %s", target.getEmail(), caseEntity.getNumber(), ownerEmail),
                LogLevel.WARNING,
                LogAction.REVOKE_ACCESS,
                caseEntity.getNumber(),
                owner.getEmail()
        );
    }

    @Transactional
    public void updateFileAccess(Long caseId, Long userId, UpdateFileAccessRequest request, String ownerEmail) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseId.toString())));
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), userId.toString())));

        if (!caseEntity.isOwner(owner)) {
            throw new AccessDeniedException(AccessDeniedMessage.OWNER_ONLY.localized(currentLang()));
        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), userId.toString())));

        fileAccessRepository.deleteAllActionsByCaseAndUser(caseId, target.getId());
        fileAccessRepository.deleteAllByCaseAndUser(caseId, target.getId());
        grantFiles(caseEntity, target, request.getFileGrants());

        log.info("File access updated for user {} in case {} by {}", target.getEmail(), caseId, ownerEmail);
        List<String> fileNames = request.getFileGrants() == null ? List.of() :
                request.getFileGrants().stream()
                        .map(g -> caseFileRepository.findById(g.fileId())
                                .map(CaseFile::getOriginalFileName)
                                .orElse("file#" + g.fileId()))
                        .toList();
        logService.log(
                String.format("User %s updated file access to case %s by %s. Files: %s",
                        target.getEmail(), caseEntity.getNumber(), ownerEmail, fileNames),
                LogLevel.WARNING,
                LogAction.GRANT_ACCESS,
                caseEntity.getNumber(),
                owner.getEmail()
        );
    }

    @Override
    @Transactional
    public void grantSogAccess(Case caseEntity, User user, List<FileGrantDto> fileGrants) {
        CaseUserAccess access = accessRepository
                .findByCaseEntityIdAndUserId(caseEntity.getId(), user.getId())
                .orElseGet(() -> CaseUserAccess.builder()
                        .caseEntity(caseEntity)
                        .user(user)
                        .permissions(new HashSet<>())
                        .build());

        Set<CasePermission> perms = new HashSet<>();
        SOG_PERMISSIONS.forEach((module, actions) -> actions.forEach(action ->
                perms.add(CasePermission.builder().module(module).action(action).build())));

        access.setPermissions(perms);
        access.setDocumentScope(DocumentAccessScope.RESTRICTED);
        access.setMemberType(CaseMemberType.SOG);
        accessRepository.save(access);

        fileAccessRepository.deleteAllActionsByCaseAndUser(caseEntity.getId(), user.getId());
        fileAccessRepository.deleteAllByCaseAndUser(caseEntity.getId(), user.getId());

        if (fileGrants != null && !fileGrants.isEmpty()) {
            List<FileGrantDto> limited = fileGrants.stream()
                    .map(g -> {
                        Set<CaseAction> actions = EnumSet.noneOf(CaseAction.class);
                        if (g.actions() != null) actions.addAll(g.actions());
                        actions.retainAll(SOG_FILE_ACTIONS);
                        if (actions.isEmpty()) actions.add(CaseAction.READ);
                        return new FileGrantDto(g.fileId(), actions);
                    })
                    .toList();
            grantFiles(caseEntity, user, limited);
        }
    }

    @Override
    public boolean isSog(Case caseEntity, User user) {
        return accessRepository.findByCaseEntityIdAndUserId(caseEntity.getId(), user.getId())
                .map(a -> a.getMemberType() == CaseMemberType.SOG)
                .orElse(false);
    }
    @Override
    @Transactional
    public void transferOwnership(Case caseEntity, User oldOwner, User newOwner, String changedBy) {
        boolean ownerChanged = oldOwner != null && !oldOwner.getId().equals(newOwner.getId());

        if (ownerChanged) {
            revokeAll(caseEntity.getId(), oldOwner.getId());
        }
        grantFullAccess(caseEntity, newOwner);

        log.info("Ownership of case {} transferred from {} to {} by {}",
                caseEntity.getNumber(),
                oldOwner != null ? oldOwner.getEmail() : "null",
                newOwner.getEmail(), changedBy);
        logService.log(
                String.format("Case %s ownership transferred from %s to %s by %s",
                        caseEntity.getNumber(),
                        oldOwner != null ? oldOwner.getEmail() : "null",
                        newOwner.getEmail(), changedBy),
                LogLevel.WARNING,
                LogAction.FULL_ACCESS,
                caseEntity.getNumber(),
                changedBy
        );
    }

    @Override
    @Transactional
    public void reassignOwner(Case caseEntity, User newOwner, String changedBy) {
        User oldOwner = caseEntity.getOwner();
        if (oldOwner != null && oldOwner.getId().equals(newOwner.getId())) {
            return;
        }

        caseEntity.setOwner(newOwner);
        if (oldOwner != null && caseEntity.hasUser(oldOwner)) {
            caseEntity.removeUser(oldOwner);
        }
        if (!caseEntity.hasUser(newOwner)) {
            caseEntity.addUser(newOwner);
        }
        caseRepository.save(caseEntity);

        transferOwnership(caseEntity, oldOwner, newOwner, changedBy);
    }

    @Override
    @Transactional
    public void revokeAllForUser(Long userId) {
        accessRepository.findAllByUserId(userId)
                .forEach(a -> revokeAll(a.getCaseEntity().getId(), userId));
    }

    private UserSettingsLanguage currentLang(){
        return getCurrentLang();
    }
}
