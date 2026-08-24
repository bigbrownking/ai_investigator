package org.di.digital.util.mapper;

import lombok.NoArgsConstructor;
import org.di.digital.dto.response.access.ModulePermissionDto;
import org.di.digital.model.cases.CasePermission;
import org.di.digital.model.enums.permission.CaseAction;
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

    public List<ModulePermissionDto> group(Set<CasePermission> perms) {
        return perms.stream()
                .collect(Collectors.groupingBy(
                        CasePermission::getModule,
                        Collectors.mapping(CasePermission::getAction, Collectors.toSet())))
                .entrySet().stream()
                .map(e -> new ModulePermissionDto(e.getKey(), e.getValue()))
                .toList();
    }
}