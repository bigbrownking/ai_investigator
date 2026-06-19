package org.di.digital.dto.response.interrogation;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationResponse {
    private Long id;
    private String type;
    private String about;
}