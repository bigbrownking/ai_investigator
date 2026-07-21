package org.di.digital.dto.response.admin;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminStatsDto {
    private long totalUsers;
    private long totalCases;
    private long totalInterrogations;
    private long totalPages;
    private long totalAudios;
    private long totalQualifications;
    private long totalIndictments;
    private long totalAiMessages;
    private long totalSelectedMessages;
    private long totalReformulatedMessages;
    private Double avgQualificationScorePercent;
}
