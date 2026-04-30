package org.di.digital.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriminalResponse {
    private Long id;
    private String type;
    private String about;
}
