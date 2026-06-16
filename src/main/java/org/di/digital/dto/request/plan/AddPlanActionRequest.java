package org.di.digital.dto.request.plan;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddPlanActionRequest {
    private String action;
    private String goal;
    private String executor;
    private String direction;
    private Integer days;
    private String deadline;
    private Integer insertAfter;
}