package org.di.digital.reporting.dto.response;

import lombok.*;

import java.time.LocalDateTime;

/**
 * One report form in the admin list: its active and draft versions.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateSummaryResponse {
    private String code;
    /** Name of the active version, or of the latest one if nothing is published yet. */
    private String name;
    private Integer activeVersion;
    private LocalDateTime activePublishedAt;
    private Integer draftVersion;
    private Integer latestVersion;
}
