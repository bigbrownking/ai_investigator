package org.di.digital.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Builder
public class RegionSummaryDto {
    private RegionStatsDto stats;
    private Page<UserProfile> users;
    private Page<CaseResponse> cases;
    private Page<AppealDto> appeals;
}
