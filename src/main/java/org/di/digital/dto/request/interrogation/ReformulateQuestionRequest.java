package org.di.digital.dto.request.interrogation;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReformulateQuestionRequest {
    private String caseNumber;
    private String personRole;
    private String question;
    private String language;
    private String style = "procedural";
    private Integer maxVariants = 3;
}
