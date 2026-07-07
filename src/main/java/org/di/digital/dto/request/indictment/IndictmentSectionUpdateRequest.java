package org.di.digital.dto.request.indictment;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IndictmentSectionUpdateRequest {
    private Integer id;
    private String text;
}