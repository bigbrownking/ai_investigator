package org.di.digital.dto.response.indictment;

import lombok.*;
import org.di.digital.dto.response.chat.ReferenceLinkDto;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndictmentSectionDto {
    private Integer id;
    private String category;
    private String text;
    private List<ReferenceLinkDto> references;
}
