package org.di.digital.dto.response.interrogation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.di.digital.model.enums.QAStatusEnum;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtherAudioResponse {
    private Long id;
    private String fieldName;
    private String text;
    private QAStatusEnum status;
    private List<AudioRecordResponse> audioRecords;
}
