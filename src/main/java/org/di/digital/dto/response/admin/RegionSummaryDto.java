package org.di.digital.dto.response.admin;

import lombok.Builder;
import lombok.Getter;
import org.di.digital.dto.response.cases.CaseResponse;
import org.di.digital.dto.response.user.UserProfile;
import org.springframework.data.domain.Page;

@Getter
@Builder
public class RegionSummaryDto {
    private RegionStatsDto stats;
    private Page<UserProfile> users;
    private Page<CaseResponse> cases;
    private Page<AppealDto> appeals;
}
