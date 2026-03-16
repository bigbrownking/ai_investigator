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
public class OtherAudioResponse {
    private Long id;
    private String fieldName;
    private String text;
    private QAStatusEnum status;
}
