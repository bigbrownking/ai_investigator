package org.di.digital.dto.response.access;


import org.di.digital.model.enums.permission.CaseAction;

import java.util.Set;

public record FileGrantDto(Long fileId, Set<CaseAction> actions) {}
