package org.di.digital.dto.response;

import lombok.*;
import org.di.digital.model.enums.AppealStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppealDto {
    private Long id;
    private Long userId;
    private String userName;
    private String userSurname;
    private String userEmail;
    private Long regionId;
    private String regionName;
    private AppealStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
