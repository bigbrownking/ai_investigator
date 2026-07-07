package org.di.digital.dto.request.osmotr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsmotrResolutionRequest {
    private List<Long> evidenceSegmentIds;
    private List<Long> returnSegmentIds;
}
