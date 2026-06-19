package org.di.digital.dto.request.interrogation;

import lombok.Data;

@Data
public class EditOtherAudioTextRequest {
    private Long otherAudioId;
    private String text;
}
