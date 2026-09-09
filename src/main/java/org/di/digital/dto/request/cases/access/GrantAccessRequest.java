package org.di.digital.dto.request.cases.access;

import lombok.Getter;
import lombok.Setter;
import org.di.digital.dto.response.access.FileGrantDto;
import org.di.digital.dto.response.access.ModulePermissionDto;
import org.di.digital.model.enums.permission.DocumentAccessScope;

import java.util.List;

@Getter
@Setter
public class GrantAccessRequest {
    private List<ModulePermissionDto> permissions;
    private DocumentAccessScope documentScope;
    private List<FileGrantDto> fileGrants;
}
