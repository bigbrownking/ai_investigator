package org.di.digital.dto.response.indictment;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndictmentSectionDto {
    private Integer id;
    private String category;
    private String text;
}
