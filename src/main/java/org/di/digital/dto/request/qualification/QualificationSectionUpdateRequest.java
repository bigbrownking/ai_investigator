package org.di.digital.dto.request.qualification;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QualificationSectionUpdateRequest {
    private Integer id;
    private String text;
}