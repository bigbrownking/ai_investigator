package org.di.digital.dto.request.search;

import lombok.*;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchRequest {
    private String iin;
    private String name;
    private String surname;
    private String fathername;
    private String email;
    private String profession;
    private String administration;
    private String region;
    private Boolean active;
}
