package org.di.digital.util.mapper;

import lombok.NoArgsConstructor;
import org.di.digital.dto.response.access.ModulePermissionDto;
import org.di.digital.model.cases.CasePermission;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class PermissionMapper {

    public Set<CasePermission> flatten(List<ModulePermissionDto> dtos) {
        Set<CasePermission> result = new HashSet<>();
        if (dtos == null) return result;
        for (ModulePermissionDto dto : dtos) {
            if (dto.actions() == null) continue;
            for (CaseAction action : dto.actions()) {
                result.add(CasePermission.builder()
                        .module(dto.module())
                        .action(action)
                        .build());
            }
        }
        return result;
    }
    public Map<CaseModule, Set<CaseAction>> groupAsMap(Set<CasePermission> perms) {
        Map<CaseModule, Set<CaseAction>> result = new EnumMap<>(CaseModule.class);
        for (CasePermission p : perms) {
            result.computeIfAbsent(p.getModule(), k -> new HashSet<>()).add(p.getAction());
        }
        return result;
    }
}