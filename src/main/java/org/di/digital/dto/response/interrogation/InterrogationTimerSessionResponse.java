package org.di.digital.dto.response.interrogation;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterrogationTimerSessionResponse {
    private LocalDateTime startedAt;
    private LocalDateTime pausedAt;
}
