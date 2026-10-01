package org.di.digital.reporting.dto.response;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Report form available to operators: its currently active version.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveFormResponse {
    private String code;
    private String name;
    private String description;
    private Integer version;
    private LocalDateTime publishedAt;
}
