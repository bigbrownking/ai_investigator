package org.di.digital.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionStatsDto {
    private Long regionId;
    private String mapCode;
    private String regionName;
    private long totalUsers;
    private long activeUsers;
    private long totalCases;
    private long pendingAppeals;
}
