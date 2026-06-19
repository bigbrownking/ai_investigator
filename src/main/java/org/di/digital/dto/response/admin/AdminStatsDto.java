package org.di.digital.dto.response.admin;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminStatsDto {
    private long totalUsers;
    private long activeUsers;
    private long inactiveUsers;
    private long totalCases;
    private long pendingAppeals;
    private long approvedAppeals;
    private long rejectedAppeals;
    private long totalInterrogations;
    private long totalPages;
    private long totalAudios;
    private long totalQualifications;
    private long totalIndictments;
}
