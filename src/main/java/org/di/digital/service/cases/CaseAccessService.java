package org.di.digital.service.cases;

import org.di.digital.dto.request.cases.access.GrantAccessRequest;
import org.di.digital.dto.request.cases.access.UpdateFileAccessRequest;
import org.di.digital.dto.response.access.FileGrantDto;
import org.di.digital.dto.response.access.MyAccessDto;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;
import org.di.digital.model.user.User;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface CaseAccessService {
    void grantInitialAccess(Case caseEntity, User user, List<FileGrantDto> fileGrants);
    void grantFullAccess(Case caseEntity, User user);
    void grantFiles(Case caseEntity, User user, List<FileGrantDto> fileGrants);
    void revokeFile(Long fileId, Long userId);
    void revokeAll(Long caseId, Long userId);
    boolean can(Case caseEntity, User user, CaseModule module, CaseAction action);
    void require(Case caseEntity, User user, CaseModule module, CaseAction action);
    boolean canAccessFile(CaseFile file, User user, CaseAction action);
    void requireFile(CaseFile file, User user, CaseAction action);
    MyAccessDto getMyPermissions(Case caseEntity, User user);
    void grantAccess(Long caseId, Long userId, GrantAccessRequest request, String ownerEmail);
    void revokeAccess(Long caseId, Long userId, String ownerEmail);
    void updateFileAccess(Long caseId, Long userId, UpdateFileAccessRequest request, String ownerEmail);
}
