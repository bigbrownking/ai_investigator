package org.di.digital.dto.response.user;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String iin;
    private String name;
    private String surname;
    private String fathername;
    private String email;
    private String profession;
    private String rank;
    private String administration;
    private String region;
}
