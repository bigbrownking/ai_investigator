package org.di.digital.util;

import org.di.digital.dto.response.*;
import org.di.digital.model.Case;
import org.di.digital.model.CaseInterrogation;
import org.di.digital.model.Role;
import org.di.digital.model.User;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class Mapper {
    public CaseInterrogationResponse mapToInterrogationResponse(CaseInterrogation interrogation) {
        return CaseInterrogationResponse.builder()
                .id(interrogation.getId())
                .iin(interrogation.getIin())
                .fio(interrogation.getFio())
                .role(interrogation.getRole())
                .date(String.valueOf(interrogation.getDate()))
                .status(interrogation.getStatus().name())
                .build();
    }

    public CaseResponse mapToCaseResponse(Case caseEntity) {
        return CaseResponse.builder()
                .id(caseEntity.getId())
                .title(caseEntity.getTitle())
                .number(caseEntity.getNumber())
                .description(caseEntity.getDescription())
                .status(caseEntity.isStatus())
                .files(caseEntity.getFiles().stream()
                        .map(f -> CaseFileResponse.builder()
                                .id(f.getId())
                                .originalFileName(f.getOriginalFileName())
                                .contentType(f.getContentType())
                                .fileSize(f.getFileSize())
                                .status(f.getStatus().getLabel())
                                .uploadedAt(String.valueOf(f.getUploadedAt()))
                                .build())
                        .collect(Collectors.toList()))
                .interrogations(caseEntity.getInterrogations().stream()
                        .map(f -> CaseInterrogationResponse.builder()
                                .id(f.getId())
                                .iin(f.getIin())
                                .fio(f.getFio())
                                .role(f.getRole())
                                .date(String.valueOf(f.getDate()))
                                .status(f.getStatus().getLabel())
                                .build())
                        .collect(Collectors.toList()))
                .users(caseEntity.getUsers().stream()
                        .map(user -> CaseUserResponse.builder()
                                .id(user.getId())
                                .email(user.getEmail())
                                .name(user.getName())
                                .surname(user.getSurname())
                                .fathername(user.getFathername())
                                .isOwner(caseEntity.isOwner(user))
                                .build())
                        .collect(Collectors.toList()))
                .createdDate(caseEntity.getCreatedDate())
                .ownerEmail(caseEntity.getOwner() != null ? caseEntity.getOwner().getEmail() : null)
                .lastActivityDate(caseEntity.getLastActivityDate())
                .lastActivityType(caseEntity.getLastActivityType())
                .qualificationGeneratedAt(caseEntity.getQualificationGeneratedAt())
                .indictmentGeneratedAt(caseEntity.getIndictmentGeneratedAt())
                .updatedDate(caseEntity.getUpdatedDate())
                .build();
    }

    public UserProfile mapToUserProfileResponse(User user) {
        String roles = user.getRoles().stream()
                .map(Role::getName)
                .sorted()
                .reduce((r1, r2) -> r1 + ", " + r2)
                .orElse("");

        UserSettingsDto settingsDto = null;
        if (user.getSettings() != null) {
            settingsDto = UserSettingsDto.builder()
                    .level(user.getSettings().getLevel() != null ? user.getSettings().getLevel().name() : null)
                    .language(user.getSettings().getLanguage().getLanguage())
                    .theme(user.getSettings().getTheme().getTheme())
                    .build();
        }

        return UserProfile.builder()
                .id(user.getId())
                .username(user.getUsername())
                .name(user.getName())
                .surname(user.getSurname())
                .fathername(user.getFathername())
                .role(roles)
                .email(user.getEmail())
                .active(user.isActive())
                .settings(settingsDto)
                .createdCaseCount(user.getCases() != null ? user.getCases().size() : 0)
                .build();
    }
}
