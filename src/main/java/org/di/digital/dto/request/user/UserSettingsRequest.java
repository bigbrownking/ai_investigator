package org.di.digital.dto.request.user;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsRequest {
    private String level;
    private String language;
    private String theme;
}