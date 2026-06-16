package org.di.digital.dto.request.osmotr;

import lombok.*;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSegmentSelectionRequest {
    private List<Long> segmentIds;
    private Boolean needed;
}