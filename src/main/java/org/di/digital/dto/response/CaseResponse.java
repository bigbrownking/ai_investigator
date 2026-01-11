package org.di.digital.dto.response;


import lombok.*;
import org.di.digital.model.CaseInterrogation;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseResponse {
    private Long id;
    private String title;
    private String number;
    private String description;
    private List<CaseFileResponse> files;
    private List<CaseInterrogationResponse> interrogations;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
