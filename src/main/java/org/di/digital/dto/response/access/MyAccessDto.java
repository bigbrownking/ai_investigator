package org.di.digital.dto.response.access;

import lombok.Builder;
import lombok.Getter;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;
import org.di.digital.model.enums.permission.DocumentAccessScope;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Builder
public class MyAccessDto {
    private Map<CaseModule, Set<CaseAction>> permissions;
    private DocumentAccessScope documentScope;
    private List<FileGrantDto> fileGrants;
}
