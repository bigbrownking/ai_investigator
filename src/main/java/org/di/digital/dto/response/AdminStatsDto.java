package org.di.digital.dto.response;

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
}
