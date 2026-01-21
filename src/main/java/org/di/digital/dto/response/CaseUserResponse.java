package org.di.digital.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseUserResponse {
    private Long id;
    private String email;
    private String name;
    private String surname;
    private String fathername;
    private boolean isOwner;
}
