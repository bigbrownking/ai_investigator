package org.di.digital.dto.request.cases;

import lombok.Data;

@Data
public class UpdateParticipantsRequest {
    private List<Long> participantIds;
}