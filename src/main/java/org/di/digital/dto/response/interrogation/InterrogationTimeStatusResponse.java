package org.di.digital.dto.response.interrogation;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterrogationTimeStatusResponse {
    private String profile;
    private long continuousSeconds;
    private long dailySeconds;
    private boolean continuousWarn;        // треб.1: 3:45 (или 2:40)
    private boolean continuousLimitReached;// треб.1: 4:00 (или 3:00)
    private boolean dailyWarn;             // треб.3: 7:30 (или 4:40)
    private boolean dailyLimitReached;     // треб.3: 8:00 (или 5:00)
    private boolean onBreak;
    private boolean breakOver;             // треб.2
    private long breakRemainingSeconds;
    private boolean categoryConfirmed;     // треб.5
}