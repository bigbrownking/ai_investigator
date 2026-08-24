package org.di.digital.dto.response.access;

import org.di.digital.model.enums.permission.DocumentAccessScope;

import java.util.List;

public record UserCaseAccessDto(
        Long caseId,
        String caseNumber,
        String caseTitle,
        boolean owner,
        DocumentAccessScope documentScope,
        List<ModulePermissionDto> permissions,
        List<FileGrantDto> fileGrants
) {
}