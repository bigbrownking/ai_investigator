package org.di.digital.dto.response.qualification;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualificationSectionDto {
    private Integer id;
    private String category;
    private String text;
}