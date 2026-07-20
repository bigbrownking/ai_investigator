package org.di.digital.dto.response.admin;

import lombok.Builder;
import lombok.Getter;
import org.di.digital.dto.response.cases.CasePreviewResponse;
import org.di.digital.dto.response.cases.CaseResponse;
import org.di.digital.dto.response.user.UserDto;
import org.di.digital.dto.response.user.UserProfile;
import org.springframework.data.domain.Page;

@Getter
@Builder
public class RegionSummaryDto {
    private RegionStatsDto stats;
    private Page<UserDto> users;
    private Page<CasePreviewResponse> cases;
    private Page<AppealDto> appeals;
}
