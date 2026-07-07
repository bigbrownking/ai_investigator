package org.di.digital.dto.request.osmotr;

import lombok.Data;

import java.util.List;

@Data
public class DistributionRequest {
    private List<Long> evidenceSegmentIds;
    private List<Long> returnSegmentIds;
}
