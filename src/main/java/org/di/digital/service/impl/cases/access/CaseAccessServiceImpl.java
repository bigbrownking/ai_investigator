package org.di.digital.service.impl.cases.access;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.cases.access.GrantAccessRequest;
import org.di.digital.dto.request.cases.access.UpdateFileAccessRequest;
import org.di.digital.dto.response.access.FileGrantDto;
import org.di.digital.dto.response.access.MyAccessDto;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.AccessDeniedMessage;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.cases.*;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;
import org.di.digital.model.enums.permission.DocumentAccessScope;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.user.User;
import org.di.digital.repository.assess.CaseFileAccessRepository;
import org.di.digital.repository.assess.CaseUserAccessRepository;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.user.UserRepository;
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

    @Transactional
    public void grantInitialAccess(Case caseEntity, User user, List<FileGrantDto> fileGrants) {
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
                        .documentScope(DocumentAccessScope.ALL)
                        .build());

        access.setPermissions(allPermissions());
        access.setDocumentScope(DocumentAccessScope.ALL);
        accessRepository.save(access);
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
                throw new IllegalStateException("Файл не принадлежит делу");
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
            throw new IllegalStateException("Нельзя изменить доступ владельца дела");
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
            throw new IllegalStateException("Нельзя отозвать доступ владельца дела");
        }

        revokeAll(caseId, userId);
        log.info("Access revoked for user {} in case {} by {}", userId, caseId, ownerEmail);
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
    }

    private UserSettingsLanguage currentLang(){
        return getCurrentLang();
    }
}
