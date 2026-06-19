package org.di.digital.dto.response.user;

import lombok.*;

import java.util.List;
import java.util.Set;

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
    private Set<String> role;
    private String profession;
    private String rank;
    private String administration;
    private String region;
    private List<String> responsibleRegions;
    private String street;
    private UserSettingsDto settings;
    private int createdCaseCount;
    private boolean faceEnabled;
    private boolean active;
    private boolean online;
    private String lastSeenAt;
}

