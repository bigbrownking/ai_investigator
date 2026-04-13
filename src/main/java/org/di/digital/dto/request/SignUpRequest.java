package org.di.digital.dto.request;

import lombok.Getter;

@Getter
public class SignUpRequest {
    private String iin;
    private String email;
    private String password;
    private String name;
    private String surname;
    private String fathername;
    private Long professionId;
    private Long administrationId;
    private Long regionId;
}
