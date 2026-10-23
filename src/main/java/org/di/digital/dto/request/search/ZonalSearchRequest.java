package org.di.digital.dto.request.search;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonalSearchRequest {
    private String name;
    private Boolean active;
    private Boolean isDeleted;
}
