package org.di.digital.dto.response.user;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserSuggestionResponse {
    private Long id;
    private String fio;
    private String email;
}
