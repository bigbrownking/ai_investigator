package org.di.digital.dto.response.admin;

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
    private long totalInterrogations;
    private long totalPages;
    private long totalAudios;
    private long totalQualifications;
    private long totalIndictments;
    private long totalAiMessages;
    private long totalSelectedMessages;
    private long totalReformulatedMessages;
}
