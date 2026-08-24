package org.di.digital.service.impl.cases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.access.FileGrantDto;
import org.di.digital.dto.response.access.ModulePermissionDto;
import org.di.digital.model.cases.*;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;
import org.di.digital.model.enums.permission.DocumentAccessScope;
import org.di.digital.model.user.User;
import org.di.digital.repository.assess.CaseFileAccessRepository;
import org.di.digital.repository.assess.CaseUserAccessRepository;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.service.cases.CaseAccessService;
import org.di.digital.util.mapper.PermissionMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseAccessServiceImpl implements CaseAccessService {
    private final CaseUserAccessRepository accessRepository;
    private final CaseFileAccessRepository fileAccessRepository;
    private final CaseFileRepository caseFileRepository;
    private final PermissionMapper permissionMapper;

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
                    .orElseThrow(() -> new RuntimeException("File not found: " + grant.fileId()));
            if (!file.getCaseEntity().getId().equals(caseEntity.getId())) {
                throw new IllegalArgumentException("Файл не принадлежит делу");
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
            throw new AccessDeniedException("Нет доступа: " + module + " / " + action);
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
            throw new AccessDeniedException("Нет доступа к файлу " + file.getId() + " / " + action);
        }
    }
    public List<CaseFile> visibleFiles(Case caseEntity, User user) {
        if (caseEntity.isOwner(user)) return caseEntity.getFiles();

        CaseUserAccess access = accessRepository
                .findByCaseEntityIdAndUserId(caseEntity.getId(), user.getId())
                .orElse(null);
        if (access == null || !access.canAccessModule(CaseModule.DOCUMENTS)) return List.of();
        if (access.getDocumentScope() == DocumentAccessScope.ALL) return caseEntity.getFiles();

        Set<Long> allowed = fileAccessRepository
                .findAccessibleFileIds(caseEntity.getId(), user.getId());
        return caseEntity.getFiles().stream()
                .filter(f -> allowed.contains(f.getId()))
                .toList();
    }
    public List<ModulePermissionDto> getMyPermissions(Case caseEntity, User user) {
        if (caseEntity.isOwner(user)) {
            return Arrays.stream(CaseModule.values())
                    .map(m -> new ModulePermissionDto(m, EnumSet.allOf(CaseAction.class)))
                    .toList();
        }
        return accessRepository.findByCaseEntityIdAndUserId(caseEntity.getId(), user.getId())
                .map(a -> permissionMapper.group(a.getPermissions()))
                .orElse(List.of());
    }
}
