package org.di.digital.dto.request.access;

import org.di.digital.dto.response.access.FileGrantDto;
import org.di.digital.dto.response.access.ModulePermissionDto;
import org.di.digital.model.enums.permission.DocumentAccessScope;

import java.util.List;

public record GrantAccessRequest(
        List<ModulePermissionDto> permissions,
        DocumentAccessScope documentScope,
        List<FileGrantDto> fileGrants
) {}
