package org.di.digital.dto.response.user;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsDto {
    private String level;
    private String language;
    private String theme;
}
