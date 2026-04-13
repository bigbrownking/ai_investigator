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
    private String iin;
    private String name;
    private String surname;
    private String fathername;
    private String email;
    private String role;
    private String profession;
    private String administration;
    private String region;
    private String street;
    private UserSettingsDto settings;
    private int createdCaseCount;
    private boolean active;
}

