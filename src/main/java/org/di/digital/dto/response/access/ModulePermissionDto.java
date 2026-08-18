package org.di.digital.dto.response.access;

import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;

import java.util.Set;

public record ModulePermissionDto(CaseModule module, Set<CaseAction> actions) {}
