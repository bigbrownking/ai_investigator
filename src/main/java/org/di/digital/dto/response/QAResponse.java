package org.di.digital.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.di.digital.model.enums.QAStatusEnum;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QAResponse {
    private Long id;
    private String question;
    private String answer;
    private int orderIndex;
    private Boolean edited;
    private QAStatusEnum status;
}
