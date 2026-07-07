package org.di.digital.dto.request.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    private String name;
    private String surname;
    private String fathername;
    private Long regionId;
    private Long administrationId;
    private Long professionId;
    private Long rankId;
}
