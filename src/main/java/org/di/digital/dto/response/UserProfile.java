package org.di.digital.dto.response;

import lombok.*;
import org.di.digital.model.UserSettings;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private Long id;
    private String username;
    private String name;
    private String surname;
    private String fathername;
    private String email;
    private String role;
    private UserSettingsDto settings;
    private int createdCaseCount;
    private boolean active;
}

